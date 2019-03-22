package com.changedistiller.test.DAO;

import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Set;

public class DBHandler {

    private final Jedis jedis;

    public DBHandler() {
        jedis = new Jedis ("192.168.199.13");
        jedis.auth("123456");
    }

    public boolean WriteToDB(String name, Set<List<String>> iSet,
                             Set<List<String>> cSet) {


        return true;
    }


    public String GenerateString(Set<List<String>> set) {
        return new String();
    }

}
