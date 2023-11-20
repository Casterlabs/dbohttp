package co.casterlabs.dbohttp.daemon;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.dbohttp.util.MarshallingContext;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.validation.JsonValidate;
import lombok.NonNull;

@JsonClass(exposeAll = true)
class QueryRequestBody extends MarshallingContext {
    public @NonNull String sql;
    public @Nullable JsonArray params;

    @JsonValidate
    private void $validate() {
        if (this.sql == null) {
            throw new IllegalArgumentException("You must specify an SQL string.");
        }
    }

}
