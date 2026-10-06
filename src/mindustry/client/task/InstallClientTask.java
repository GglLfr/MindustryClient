package mindustry.client.task;

import arc.util.*;
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
import java.nio.channels.*;
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

    /** Creates the task, along with property conventions. */
    @Inject
    public InstallClientTask(ProviderFactory providers, ProjectLayout layout){
        var dir = providers.gradleProperty("mindustryInstallPath").map(File::new).orElse(new File(OS.getAppDataDirectoryString("Mindustry"), "clients"));
        getClientFile().set(layout.file(getBuildNumber()
            .zip(getBuildType(), (num, type) -> String.format("Mindustry-%s-%s.jar", type, num))
            .zip(dir, (name, d) -> new File(d, name))
        ));
    }

    /** Downloads the client, or pass through if an existing client is detected. */
    @TaskAction
    public void install(){
        var client = getClient().get();
        if(client.path() != null || !client.ignoreSteam() && client.steamPath() != null) return;

        var dest = getClientFile().get().getAsFile().toPath();
        try{
            Files.createDirectories(dest.getParent());
        }catch(IOException e){
            throw new GradleException("Couldn't create clients directory", e);
        }

        var logger = getLogger();
        var num = getBuildNumber().get();
        var type = getBuildType().get();

        var http = HttpClient.newBuilder()
            .followRedirects(Redirect.NORMAL)
            .build();

        var request = HttpRequest.newBuilder()
            .uri(URI.create(switch(type){
                case official ->
                    String.format("https://github.com/Anuken/Mindustry/releases/download/v%s/Mindustry.jar", num);
                case bleedingEdge ->
                    String.format("https://github.com/Anuken/MindustryBuilds/releases/download/%s/Mindustry-BE-Desktop-%s.jar", num, num);
            }))
            .GET()
            .build();

        var lockPath = dest.resolveSibling(dest.getFileName() + ".lock");
        try(var lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            @SuppressWarnings("unused") var lock = lockChannel.lock()
        ){
            if(isClientJar(dest)) return;

            logger.lifecycle("Installing client...");
            var response = http.send(request, BodyHandlers.ofInputStream());

            try(var in = checkResponse(response); var out = Files.newOutputStream(dest)){
                var totalBytesOpt = response.headers().firstValueAsLong("Content-Length");
                if(totalBytesOpt.isEmpty() || totalBytesOpt.getAsLong() == 0){
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

            if(!isClientJar(dest)) throw new IOException("Downloaded file is not a Mindustry client JAR");
            logger.lifecycle(String.format("Installed %s client version %s!", type, num));
        }catch(IOException | InterruptedException e){
            throw new GradleException("Couldn't download client", e);
        }
    }

    private static <T extends Closeable> T checkResponse(HttpResponse<T> response) throws IOException{
        if(response.statusCode() != 200) try(@SuppressWarnings("unused") var body = response.body()){
            throw new IOException(String.format("HTTP %d from %s", response.statusCode(), response.uri()));
        }
        return response.body();
    }
}
