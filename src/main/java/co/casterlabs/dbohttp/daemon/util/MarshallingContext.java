package co.casterlabs.dbohttp.daemon.util;

import java.sql.Blob;
import java.sql.SQLException;

import co.casterlabs.dbohttp.daemon.database.QueryMarshallingException;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;

@JsonClass(exposeAll = true)
public class MarshallingContext {
    public boolean byteArraysAreSigned = true;

    public Object jsonToJava(JsonElement e) throws QueryMarshallingException {
        if (e.isJsonArray()) {
            try {
                // We support BLOB via a JsonArray
                JsonArray jsonArray = e.getAsArray();
                byte[] bytes = new byte[jsonArray.size()];

                for (int idx = 0; idx < jsonArray.size(); idx++) {
                    Number number = jsonArray.getNumber(idx);

                    if (number.doubleValue() != number.longValue()) {
                        throw new QueryMarshallingException(new IllegalArgumentException("Every entry in a byte array must be an integer."));
                    }

                    long value = number.longValue();

                    if (this.byteArraysAreSigned) {
                        if (value < -128 || value > 127) {
                            throw new QueryMarshallingException(new IllegalArgumentException("Signed values must be -128->127 inclusive."));
                        }
                    } else {
                        if (value < 0 || value > 255) {
                            throw new QueryMarshallingException(new IllegalArgumentException("Unsigned values must be 0->255 inclusive."));
                        }
                    }

                    bytes[idx] = (byte) value;
                }

                return bytes;
            } catch (UnsupportedOperationException ignored) {
                throw new QueryMarshallingException(new UnsupportedOperationException("Cannot map non-byte arrays to SQL."));
            }
        }

        if (e.isJsonBoolean()) {
            return e.getAsBoolean() ? 1 : 0; // SQLite does not have a Boolean type.
        }

        if (e.isJsonNull()) {
            return null;
        }

        if (e.isJsonNumber()) {
            return e.getAsNumber();
        }

        if (e.isJsonObject()) {
            throw new QueryMarshallingException(new UnsupportedOperationException("Cannot map JsonObject to SQL."));
        }

        if (e.isJsonString()) {
            return e.getAsString();
        }

        throw new UnsupportedOperationException("Unknown type: " + JsonElement.class);
    }

    public JsonElement javaToJson(Object obj) throws QueryMarshallingException {
        if (obj instanceof Blob) {
            Blob blob = (Blob) obj;
            try {
                byte[] bytes = blob.getBytes(0, (int) blob.length());
                blob.free();

                return bytesToArray(bytes);
            } catch (SQLException e) {
                throw new QueryMarshallingException(e);
            }
        }

        if (obj instanceof byte[]) {
            return bytesToArray((byte[]) obj);
        }

        return Rson.DEFAULT.toJson(obj);
    }

    private JsonElement bytesToArray(byte[] bytes) {
        JsonArray arr = new JsonArray();
        for (byte b : bytes) {
            if (this.byteArraysAreSigned) {
                arr.add(b);
            } else {
                arr.add(Byte.toUnsignedInt(b));
            }
        }
        return arr;
    }

}
