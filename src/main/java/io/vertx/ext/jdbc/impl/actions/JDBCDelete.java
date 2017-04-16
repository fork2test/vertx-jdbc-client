package io.vertx.ext.jdbc.impl.actions;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import java.sql.*;
import java.util.StringJoiner;

/**
 * Created by fiorenzo on 18/01/17.
 */
public class JDBCDelete extends AbstractJDBCAction<UpdateResult> {
/*
DELETE FROM table_name WHERE some_column=some_value;
 */

  private final String table;
  private final JsonObject key;
  private final JsonArray in;
  private final int timeout;
  private String sql;


  public JDBCDelete(Vertx vertx, JDBCStatementHelper helper, Connection connection, ContextInternal ctx, TaskQueue statementsQueue, int timeout, String table, JsonObject key) {
    super(vertx, helper, connection, ctx, statementsQueue);
    this.key = key;
    this.table = table;
    this.timeout = timeout;
    this.in = new JsonArray();
    init();
  }

  private void init() {
    StringJoiner where = new StringJoiner("=? AND ", "DELETE FROM " + table + " WHERE ", "=? ");
    this.key.stream().forEach(
      coppia -> {
        where.add(coppia.getKey());
        this.in.add(coppia.getValue());
      }
    );
    this.sql = where.toString();
  }

  @Override
  protected UpdateResult execute() throws SQLException {
    final boolean returKeys = true;
    try (PreparedStatement statement = conn.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
      if (timeout >= 0) {
        statement.setQueryTimeout(timeout);
      }

      helper.fillStatement(statement, in);

      int updated = statement.executeUpdate();
      JsonArray keys = new JsonArray();

      // Create JsonArray of keys
      if (returKeys) {
        ResultSet rs = null;
        try {
          // the resource might also fail
          // specially on oracle DBMS
          rs = statement.getGeneratedKeys();
          if (rs != null) {
            while (rs.next()) {
              Object key = rs.getObject(1);
              if (key != null) {
                keys.add(helper.convertSqlValue(key));
              }
            }
          }
        } catch (SQLException e) {
          // do not crash if no permissions
        } finally {
          if (rs != null) {
            try {
              rs.close();
            } catch (SQLException e) {
              // ignore close error
            }
          }
        }
      }

      return new UpdateResult(updated, keys);
    }
  }


  @Override
  protected String name() {
    return "delete";
  }
}
