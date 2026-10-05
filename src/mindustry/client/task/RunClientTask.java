package mindustry.client.task;

import arc.util.*;
import mindustry.client.*;
import mindustry.client.service.*;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;
import org.gradle.process.*;

import javax.inject.*;
import java.util.*;

import static mindustry.client.MindustryClientPlugin.*;

/** Task to run Mindustry client, whether through a detected client or a downloaded one. */
public abstract class RunClientTask extends DefaultTask{
    /** @return The client jar from {@link InstallClientTask}. */
    public abstract @InputFiles ConfigurableFileCollection getClientClasspaths();

    /** @return {@link MindustryClientService#detected}. */
    public abstract @Input Property<ClientInfo> getClient();

    /** @return Exec/Java-exec operations. */
    public abstract @Inject ExecOperations getExecOperations();

    /** Runs the Mindustry client. */
    @TaskAction
    public void run(){
        var logger = getLogger();
        var client = getClient().get();
        var jvmAgs = List.of(
            "-Dhttps.protocols=TLSv1.2,TLSv1.1,TLSv1",
            "--enable-native-access=ALL-UNNAMED"
        );

        if(client.isSteam()){
            logger.lifecycle("Running Mindustry via Steam, so stdin/stdout is not captured.");
            logger.lifecycle("This Gradle task will exit immediately, but Mindustry is being run at the background.");
            logger.lifecycle("Give Steam some time to boot Mindustry up.");

            var uri = "steam://run/1127400";
            getExecOperations().exec(e -> {
                if(OS.isWindows) e.commandLine("cmd", "/c", "start", "Mindustry", uri);
                else if(OS.isMac) e.commandLine("open", uri);
                else if(OS.isLinux) e.commandLine("xdg-open", uri);
                else throw new GradleException(String.format("Unsupported host OS %s", OS.osName));
            });
        }else if(client.path() != null){
            if(isClientJar(client.path())){
                getExecOperations().javaexec(e -> {
                    e.getMainClass().set("mindustry.desktop.DesktopLauncher");
                    e.jvmArgs(jvmAgs);
                    e.classpath(client.path());
                });
            }else{
                getExecOperations().exec(e -> {
                    if(OS.isWindows) e.commandLine("cmd", "/c", client.path());
                    else if(OS.isMac || OS.isLinux) e.commandLine(client.path());
                    else throw new GradleException(String.format("Unsupported host OS %s", OS.osName));
                });
            }
        }else{
            getExecOperations().javaexec(e -> {
                e.getMainClass().set("mindustry.desktop.DesktopLauncher");
                e.jvmArgs(jvmAgs);
                e.classpath(getClientClasspaths());
            });
        }
    }
}