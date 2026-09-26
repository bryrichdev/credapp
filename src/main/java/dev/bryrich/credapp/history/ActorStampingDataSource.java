package dev.bryrich.credapp.history;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Tells the database who is signed in, so the change-history triggers can record who made
 * each change.
 *
 * Every connection taken from the pool is stamped with the current user, or cleared when no
 * one is signed in. A pooled connection therefore never carries the last borrower's name.
 * The user comes from {@link ActorFilter}, never from the security context directly.
 */
class ActorStampingDataSource extends DelegatingDataSource {

    ActorStampingDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return stamp(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return stamp(super.getConnection(username, password));
    }

    private static Connection stamp(Connection connection) throws SQLException {
        CurrentActor.Actor actor = CurrentActor.get();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('credapp.actor_id', ?, false), set_config('credapp.actor_email', ?, false)")) {
            statement.setString(1, actor == null || actor.id() == null ? "" : actor.id().toString());
            statement.setString(2, actor == null ? "" : actor.email());
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
