package mindustry.client.service;

import arc.util.*;
import mindustry.client.*;
import mindustry.client.ClientInfo.*;
import org.gradle.api.*;
import org.gradle.api.logging.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.*;

import java.io.*;

/**
 * Gradle build service that provides Mindustry client detection. Ensures client detection happens exactly once (on
 * demand) per build.
 */
public abstract class MindustryClientService implements BuildService<MindustryClientParams>{
    private final Logger logger = Logging.getLogger(MindustryClientService.class);

    /** The detected client info. */
    public final ClientInfo detected = ClientInfo.detect(
        getParameters().getIgnoreSteam().get(),
        getParameters().getPath().getAsFile().getOrNull(),
        new File(OS.getAppDataDirectoryString("Mindustry")),
        convertOS(),
        new ClientInfo.Logger(){
            @Override
            public void lifecycle(String log){
                logger.lifecycle(log);
            }

            @Override
            public void warn(String log){
                logger.warn(log);
            }
        }
    );

    private static Os convertOS(){
        if(OS.isWindows) return Os.windows;
        else if(OS.isMac) return Os.macOS;
        else if(OS.isLinux) return Os.linux;
        else throw new GradleException(String.format("Unsupported host OS `%s`", OS.osName));
    }
}