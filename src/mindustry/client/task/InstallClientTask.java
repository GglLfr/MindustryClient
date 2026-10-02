package mindustry.client.task;

import mindustry.client.*;
import mindustry.client.service.*;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;

import javax.inject.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpClient.*;
import java.net.http.HttpResponse.*;
import java.nio.file.*;

import static mindustry.client.MindustryClientPlugin.*;

/** Task to install Mindustry client, or pass through if an existing client is detected. */
public abstract class InstallClientTask extends DefaultTask{
    /** @return {@code build} property in {@code version.properties}. */
    public abstract @Input Property<String> getBuildNumber();

    /** @return {@code type} property in {@code version.properties}. */
    public abstract @Input Property<ClientType> getBuildType();

    /** @return {@link MindustryClientService#detected}. */
    public abstract @Input Property<ClientInfo> getClient();

    /** @return The resulting client file, if downloaded. */
    public abstract @OutputFile RegularFileProperty getClientFile();

    /** @return The injected project layout. */
    protected abstract @Inject ProjectLayout getLayout();

    /** Creates the task, along with property conventions. */
    @Inject
    public InstallClientTask(){
        getClientFile().convention(getBuildNumber().flatMap(num -> getBuildType().flatMap(type -> getLayout().getBuildDirectory().file(String.format("clients/Mindustry-%s-%s.jar", type, num)))));
    }

    /** Downloads the client, or pass through if an existing client is detected. */
    @TaskAction
    public void install(){
        var client = getClient().get();
        if(client.path() != null || !client.ignoreSteam() && client.steamPath() != null) return;

        var dest = getClientFile().get().getAsFile();
        mkdirs(dest.getParentFile());

        if(dest.exists() && isClientJar(dest)) return;

        var logger = getLogger();
        logger.lifecycle("Installing client...");

        var num = getBuildNumber().get();
        var type = getBuildType().get();

        try(var http = HttpClient.newBuilder()
            .followRedirects(Redirect.NORMAL)
            .build()){
            var request = HttpRequest.newBuilder()
                .uri(URI.create(switch(type){
                    case official ->
                        String.format("https://github.com/Anuken/Mindustry/releases/download/v%s/Mindustry.jar", num);
                    case bleedingEdge ->
                        String.format("https://github.com/Anuken/MindustryBuilds/releases/download/%s/Mindustry-BE-Desktop-%s.jar", num, num);
                }))
                .GET()
                .build();

            var response = http.send(request, BodyHandlers.ofInputStream());
            try(var in = response.body(); var out = Files.newOutputStream(dest.toPath())){
                var totalBytesOpt = response.headers().firstValueAsLong("Content-Length");
                if(totalBytesOpt.isEmpty()){
                    in.transferTo(out);
                }else{
                    long totalBytes = totalBytesOpt.getAsLong();

                    var buf = new byte[65536];
                    long totalRead = 0;

                    System.out.print("Downloading client file...");

                    int read;
                    while((read = in.read(buf)) != -1){
                        System.out.flush();

                        out.write(buf, 0, read);
                        totalRead += read;

                        System.out.printf(
                            "\rDownloading client file: %.2f MiB / %.2f MiB (%.0f%%)",
                            totalRead / (1024f * 1024f),
                            totalBytes / (1024f * 1024f),
                            totalRead * 100f / totalBytes
                        );
                    }
                    System.out.println();
                }
            }
        }catch(IOException | InterruptedException e){
            throw new GradleException("Couldn't download client", e);
        }

        logger.lifecycle(String.format("Installed %s client version %s!", type, num));
    }
}