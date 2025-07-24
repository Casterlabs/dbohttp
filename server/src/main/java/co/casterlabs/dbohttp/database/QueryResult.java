package co.casterlabs.dbohttp.database;

import java.util.List;
import java.util.Map;

import co.casterlabs.dbohttp.util.Profiler;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonElement;

public record QueryResult(List<Map<String, JsonElement>> rows, Profiler profiler) {

    public JsonElement rowsJson() {
        return Rson.DEFAULT.toJson(this.rows);
    }

}
