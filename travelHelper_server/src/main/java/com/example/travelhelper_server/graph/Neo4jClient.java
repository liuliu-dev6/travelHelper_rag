package com.example.travelhelper_server.graph;

import jakarta.annotation.PreDestroy;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Neo4j 连接封装（Bolt 协议）。
 */
@Component
public class Neo4jClient implements AutoCloseable {

    private final Driver driver;

    public Neo4jClient(@Value("${neo4j.uri}") String uri,
                       @Value("${neo4j.username}") String username,
                       @Value("${neo4j.password}") String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }

    public Session session() {
        return driver.session();
    }

    /** 连通性验证 */
    public void verify() {
        try (Session s = session()) {
            s.run("RETURN 1").consume();
        }
    }

    @PreDestroy
    @Override
    public void close() {
        driver.close();
    }
}
