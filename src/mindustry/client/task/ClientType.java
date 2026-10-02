package mindustry.client.task;

import java.io.*;

/** Either bleeding-edge or official release. */
public enum ClientType implements Serializable{
    /** {@code official}. */
    official,
    /** {@code bleeding-edge} */
    bleedingEdge;

    /**
     * @param type {@code official} or {@code bleeding-edge}.
     * @return The in-code representation of the {@code type} property in {@code version.properties}.
     */
    public static ClientType of(String type){
        return type == null ? null : switch(type){
            case "official" -> official;
            case "bleeding-edge" -> bleedingEdge;
            default ->
                throw new IllegalArgumentException(String.format("Invalid Mindustry version type `%s`; cannot install and run client from Gradle", type));
        };
    }
}
