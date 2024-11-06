package org.remapper.dto;

import com.google.gson.*;

import java.lang.reflect.Type;

public class LocationDeserializer implements JsonDeserializer<EntityMatchingJSON.Location> {

    @Override
    public EntityMatchingJSON.Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        if (jsonObject.has("container") && jsonObject.has("type") && jsonObject.has("name")) {
            return context.deserialize(json, EntityMatchingJSON.EntityLocation.class);
        } else if (jsonObject.has("method") && jsonObject.has("type") && jsonObject.has("expression")) {
            return context.deserialize(json, EntityMatchingJSON.StatementLocation.class);
        } else {
            throw new JsonParseException("Unknown location type");
        }
    }
}
