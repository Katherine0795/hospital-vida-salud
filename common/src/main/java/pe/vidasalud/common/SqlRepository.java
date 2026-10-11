package pe.vidasalud.common;

import io.r2dbc.spi.*;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.*;

public abstract class SqlRepository {
  protected final DatabaseClient db;

  protected SqlRepository(DatabaseClient db) {
    this.db = db;
  }

  public record NullValue(Class<?> type) {}

  public static NullValue nil(Class<?> type) {
    return new NullValue(type);
  }

  public static Map<String, Object> values(Object... pairs) {
    Map<String, Object> result = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    return result;
  }

  protected DatabaseClient.GenericExecuteSpec query(String sql, Map<String, Object> params) {
    var q = db.sql(sql);
    for (var p : params.entrySet()) {
      if (p.getValue() instanceof NullValue n) q = q.bindNull(p.getKey(), n.type());
      else if (p.getValue() == null) q = q.bindNull(p.getKey(), String.class);
      else q = q.bind(p.getKey(), p.getValue());
    }
    return q;
  }

  protected Mono<Long> execute(String sql, Map<String, Object> p) {
    return query(sql, p).fetch().rowsUpdated();
  }

  protected <T> Flux<T> many(
      String sql, Map<String, Object> p, BiFunction<Row, RowMetadata, T> mapper) {
    return query(sql, p).map(mapper).all();
  }

  protected <T> Mono<T> one(
      String sql, Map<String, Object> p, BiFunction<Row, RowMetadata, T> mapper) {
    return many(sql, p, mapper).next();
  }
}
