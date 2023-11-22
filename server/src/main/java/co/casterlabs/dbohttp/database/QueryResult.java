package co.casterlabs.dbohttp.database;

import co.casterlabs.dbohttp.util.Profiler;
import co.casterlabs.rakurai.json.element.JsonArray;

public record QueryResult(JsonArray rows, Profiler profiler) {

}
