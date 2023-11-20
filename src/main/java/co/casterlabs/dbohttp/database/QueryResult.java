package co.casterlabs.dbohttp.database;

import java.util.List;

import co.casterlabs.rakurai.json.element.JsonObject;

public record QueryResult(List<JsonObject> rows, double took) {

}
