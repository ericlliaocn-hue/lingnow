package cc.lingnow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON write error", e);
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isEmpty()) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("JSON read error", e);
            return null;
        }
    }
}
