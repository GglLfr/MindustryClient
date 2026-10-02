package mindustry.client;

import mindustry.client.service.*;
import mindustry.client.task.*;
import org.gradle.api.*;

import java.io.*;
import java.util.*;
import java.util.jar.Attributes.*;
import java.util.jar.*;

/** The Gradle plugin for Mindustry client detection, installation, and running. */
public class MindustryClientPlugin implements Plugin<Project>{
    /** The registered service name for {@link MindustryClientService}. */
    public static final String serviceName = "mindustryClient";

    @Override
    public void apply(Project target){
        var gradle = target.getGradle();
        var providers = target.getProviders();
        var layout = target.getLayout();

        var client = gradle.getSharedServices().registerIfAbsent(serviceName, MindustryClientService.class, s -> s.parameters(p -> {
            p.getIgnoreSteam().set(providers.gradleProperty("mindustryIgnoreSteam").map(Boolean::valueOf).orElse(false));
            p.getPath().set(layout.getProjectDirectory().file(providers.gradleProperty("mindustryPath")));
        }));

        String mindustryVersion;
        ClientType mindustryType;
        try(var stream = MindustryClientPlugin.class.getClassLoader().getResourceAsStream("version.properties")){
            if(stream == null)
                throw new IOException("Missing resource; fix your dependency specs in `build.gradle[.kts]`");

            var props = new Properties();
            props.load(stream);

            mindustryVersion = props.getProperty("build");
            mindustryType = ClientType.of(props.getProperty("type"));

            if(mindustryVersion == null || mindustryType == null)
                throw new IOException("Missing `build` or `type` properties");
        }catch(Exception e){
            throw new GradleException("Couldn't read `version.properties`", e);
        }

        var installClient = target.getTasks().register("installClient", InstallClientTask.class, t -> {
            t.setDescription("Installs a Mindustry client compatible with `mindustryVersion` from `gradle.properties`.");
            t.usesService(client);

            t.getBuildNumber().set(mindustryVersion);
            t.getBuildType().set(mindustryType);
            t.getClient().set(client.map(s -> s.detected));
            t.getClient().disallowChanges();
        });

        target.getTasks().register("run", RunClientTask.class, t -> {
            t.setDescription("Installs the mod and runs Mindustry.");

            t.getClient().set(client.map(s -> s.detected));
            t.getClient().disallowChanges();
            t.getClientClasspaths().from(installClient.flatMap(InstallClientTask::getClientFile));
        });
    }

    /**
     * Calls {@link File#mkdirs()} if it's not {@code null}.
     * @param file The file.
     */
    public static void mkdirs(File file){
        if(file != null) file.mkdirs();
    }

    /**
     * Client jar file validation.
     * @param file The file.
     * @return Whether the file exists and is a JAR.
     */
    public static boolean isClientJar(File file){
        if(file == null || !file.exists()) return false;
        try(var jar = new JarFile(file)){
            var manifest = jar.getManifest();
            if(manifest == null) return false;

            var main = manifest.getMainAttributes().getValue(Name.MAIN_CLASS);
            return main != null && main.equals("mindustry.desktop.DesktopLauncher");
        }catch(IOException e){
            return false;
        }
    }
}