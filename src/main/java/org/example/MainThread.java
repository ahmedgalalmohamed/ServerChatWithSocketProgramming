package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.*;
import java.util.*;

public class MainThread extends Thread {
    DatagramSocket socket;
    static Map<String, String> USER_STATE = new HashMap<>();

    static HashSet<String> INAPPROPRIATE = new HashSet<>(List.of("bloody", "bastard", "foolish", "stupid", "damned", "insane"));


    DatagramPacket packet;
    byte[] buffer = new byte[1024];


    MainThread(DatagramSocket socket, DatagramPacket packet, byte[] buffer) {
        this.packet = packet;
        this.socket = socket;
        this.buffer = buffer;
    }

    @Override
    public void run() {
        String msg = new String(buffer);
        msg = msg.trim();
        ConnectDB connectDB = new ConnectDB();
        Connection con = null;
        try {
            con = connectDB.ConnectedDB();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String[] rules = msg.split(";");
        String state = rules[rules.length - 1].toLowerCase();
        String user_ = rules[rules.length - 2].toLowerCase();
        System.out.println("user : " + user_);
        if (!USER_STATE.containsKey(user_)) USER_STATE.put(user_, state);
        else USER_STATE.computeIfPresent(user_, (key, value) -> state);
        switch (rules[0]) {
            case "Login" -> {
                String username = rules[1];
                String password = rules[2];
                String stattoensurelogin = "SELECT * FROM users WHERE Username=?";
                try {
                    PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
                    preparedStatement.setString(1, username);
                    ResultSet res = preparedStatement.executeQuery();

                    if (!res.isBeforeFirst()) {
                        buffer = "404".getBytes();
                    } else {
                        res.next();
                        String pass = (res.getString("Password"));
                        if (!pass.equals(password)) buffer = "401".getBytes();
                        else {
                            String sqlUpdate = "UPDATE users " + "SET port = ? , address = ?" + "WHERE Username = ?";
                            preparedStatement = con.prepareStatement(sqlUpdate);
                            preparedStatement.setString(2, packet.getAddress().getHostAddress());
                            preparedStatement.setString(3, username);
                            int port = -1;
                            while (true) {
                                try {
                                    port = (int) (Math.random() * (6000 - 1024) + 1024);
                                    preparedStatement.setInt(1, port);
                                    int re = preparedStatement.executeUpdate();
                                    break;
                                } catch (Exception ignored) {
                                }
                            }

                            buffer = (String.valueOf(port)).getBytes();
                        }
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            case "Register" -> {
                String name = rules[1];
                String username = rules[2];
                String password = rules[3];
                String stattoensureinsert = "Insert Into users (Name,Username,Password,port,address) values(?,?,?,NULL,NULL)";
                try {
                    PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureinsert);
                    preparedStatementinsert.setString(1, name);
                    preparedStatementinsert.setString(2, username);
                    preparedStatementinsert.setString(3, password);
                    preparedStatementinsert.addBatch();
                    int[] rowadd = preparedStatementinsert.executeBatch();
                    if (rowadd.length >= 1) buffer = "200".getBytes();
                    preparedStatementinsert.close();
                } catch (Exception e) {
                    buffer = "505".getBytes();
                }
            }
            case "Leave" -> {
                String user = rules[1];
                int port = Integer.parseInt(rules[2]);
                String sqlUpdate = "UPDATE users " + "SET port = NULL , address = NULL " + "WHERE Username = ?";
                try {
                    PreparedStatement preparedStatementinsert = con.prepareStatement(sqlUpdate);
                    preparedStatementinsert.setString(1, user);
                    preparedStatementinsert.executeUpdate();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                USER_STATE.remove(user);
            }
            case "chat_contact" -> {
                String message = rules[1];
                String sender = rules[2];
                String receiver = rules[3];
                String stattoensureinsert = "Insert Into  contacts_chat (Message,Sender,Receiver) values(?,?,?)";
                try {
                    PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureinsert);
                    preparedStatementinsert.setString(1, message);
                    preparedStatementinsert.setString(2, sender);
                    preparedStatementinsert.setString(3, receiver);
                    preparedStatementinsert.addBatch();
                    int[] rowadd = preparedStatementinsert.executeBatch();
                    if (rowadd.length >= 1) buffer = "200".getBytes();
                    preparedStatementinsert.close();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                one_to_one(con, sender, receiver);
            }
            case "chat_mul_contacts" -> {
                String message = rules[1];
                String sender = rules[2];
                String[] receivers = rules[3].split("&");
                System.out.println(rules[3]);
                for (String receiver : receivers) {
                    String stattoensureinsert = "Insert Into  contacts_chat (Message,Sender,Receiver) values(?,?,?)";
                    try {
                        PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureinsert);
                        preparedStatementinsert.setString(1, message);
                        preparedStatementinsert.setString(2, sender);
                        preparedStatementinsert.setString(3, receiver);
                        preparedStatementinsert.addBatch();
                        preparedStatementinsert.executeBatch();
                        preparedStatementinsert.close();
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                    one_to_one(con, sender, receiver);
                }
            }
            case "get_my_groups" -> {
                String username = rules[1];
                String stattoensurelogin = "SELECT g.grp_name FROM groups as g WHERE g.member = ? and g.kick = '0' ";
                try {
                    PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
                    preparedStatement.setString(1, username);
                    ResultSet res = preparedStatement.executeQuery();
                    StringBuilder mt = new StringBuilder();
                    while (res.next()) {
                        mt.append(res.getString("grp_name")).append(";");
                    }
                    buffer = mt.toString().getBytes();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            case "get_all_groups" -> {
                String username = rules[1];
                String stattoensurelogin = "SELECT g.grp_name FROM groups as g WHERE g.grp_name NOT IN(SELECT g.grp_name FROM groups as g WHERE g.member = ? );";
                try {
                    PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
                    preparedStatement.setString(1, username);
                    ResultSet res = preparedStatement.executeQuery();
                    StringBuilder mt = new StringBuilder();
                    while (res.next()) {
                        mt.append(res.getString("grp_name")).append(";");
                    }
                    buffer = mt.toString().getBytes();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            case "get_contact" -> {
                String username = rules[1];
                String stattoensurelogin = "SELECT c.Friend FROM contactlist as c WHERE c.Username = ?";
                try {
                    PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
                    preparedStatement.setString(1, username);
                    ResultSet res = preparedStatement.executeQuery();
                    StringBuilder mt = new StringBuilder();
                    while (res.next()) {
                        mt.append(res.getString("Friend")).append(";");
                    }
                    buffer = mt.toString().getBytes();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

            }
            case "chat_group" -> {
                String message = rules[1];
                String sender = rules[2];
                String receiver = rules[3];


                if (search_inappropriate_words(con, receiver, sender, message)) {
                    try {
                        buffer = (receiver + ";1").getBytes();
                        packet = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                        socket.send(packet);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return;
                }
                String stattoensureinsert = "Insert Into  groups_chat (grp_name,message,sender) values(?,?,?)";
                try {
                    buffer = (receiver + ";0").getBytes();
                    PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureinsert);
                    preparedStatementinsert.setString(1, receiver);
                    preparedStatementinsert.setString(2, message);
                    preparedStatementinsert.setString(3, sender);
                    preparedStatementinsert.addBatch();
                    int[] rowadd = preparedStatementinsert.executeBatch();
                    if (rowadd.length >= 1) buffer = "200;0".getBytes();
                    preparedStatementinsert.close();
                    packet = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                    socket.send(packet);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

                one_to_many(con, sender, receiver);
            }
            case "get_message_contacts" -> {
                String sender = rules[1];
                String receiver = rules[2];
                one_to_one(con, sender, receiver);
            }
            case "add_contact" -> {
                String username = rules[1];
                String friend = rules[2];
                String stattoensurelogin = "INSERT INTO `contactlist` (`Username`, `Friend`)  VALUES (?, ?), (?, ?)";
                try {
                    PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, friend);
                    preparedStatement.setString(3, friend);
                    preparedStatement.setString(4, username);
                    preparedStatement.addBatch();
                    int[] res = preparedStatement.executeBatch();
                    if (res.length >= 1) buffer = "200".getBytes();
                    else buffer = "404".getBytes();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            case "create_group" -> {
                String username = rules[1];
                String grp_name = rules[2];
                String statcheck = "SELECT * FROM groups as g WHERE g.grp_name = ?";
                try {
                    PreparedStatement statement = con.prepareStatement(statcheck);
                    statement.setString(1, grp_name);
                    ResultSet res = statement.executeQuery();
                    if (res.isBeforeFirst()) {
                        buffer = "404".getBytes();
                    } else {
                        join_group(con, grp_name, username);
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            case "join_group" -> {
                String username = rules[1];
                String grp_name = rules[2];
                join_group(con, grp_name, username);
            }
            case "get_message_groups" -> {
                String grp_name = rules[1];
                one_to_many(con, user_, grp_name);
            }
            case "get_user" -> {
                String username = rules[1];
                get_user(con, username);
            }
        }
        packet = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
        try {
            if (!(rules[0].equals("get_message_contacts") || rules[0].equals("chat_contact") || rules[0].equals("chat_group") || rules[0].equals("get_message_groups") || rules[0].equals("chat_mul_contacts")))
                socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void get_user(Connection con, String username) {
        String stattoensurelogin = "SELECT u.Username FROM users as u WHERE u.Username NOT IN(SELECT c.Friend FROM contactlist as c WHERE c.Username = ?) and u.Username NOT IN(?)";
        try {
            PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, username);
            ResultSet res = preparedStatement.executeQuery();
            StringBuilder mt = new StringBuilder();
            while (res.next()) {
                mt.append(res.getString("Username")).append(";");
            }
            buffer = mt.toString().getBytes();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void join_group(Connection con, String grp_name, String username) {
        String stattoensurelogin = "INSERT INTO `groups` (`grp_name`, `member`) VALUES (?, ?)";
        try {
            PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
            preparedStatement.setString(1, grp_name);
            preparedStatement.setString(2, username);
            preparedStatement.addBatch();
            int[] res = preparedStatement.executeBatch();
            if (res.length >= 1) buffer = "200".getBytes();
            else buffer = "400".getBytes();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void one_to_one(Connection con, String sender, String receiver) {
        String stattoensureselect = "SELECT * FROM contacts_chat as cc WHERE (cc.Sender = ? and cc.Receiver = ?) or (cc.Sender = ? and cc.Receiver = ?)  ";
        try {
            PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureselect);
            preparedStatementinsert.setString(1, sender);
            preparedStatementinsert.setString(2, receiver);
            preparedStatementinsert.setString(3, receiver);
            preparedStatementinsert.setString(4, sender);
            ResultSet rs = preparedStatementinsert.executeQuery();
            StringBuilder mt = new StringBuilder();
            while (rs.next()) {
                mt.append("\n").append(rs.getString("sender")).append(" : ").append(rs.getString("Message"));
            }
            per_to_per(con, sender, receiver, mt.toString());
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void per_to_per(Connection con, String sender, String receiver, String msg) {
        //SELECT u.port,u.address FROM users as u WHERE u.Username IN("ahmed","omar") and u.port IS NOT NULL
        String stattoensureselect = "SELECT * FROM users as u WHERE u.Username IN(?,?) and u.port IS NOT NULL";
        try {
            PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureselect);
            preparedStatementinsert.setString(1, sender);
            preparedStatementinsert.setString(2, receiver);
            ResultSet rs = preparedStatementinsert.executeQuery();
            sender = sender.toLowerCase();
            receiver = receiver.toLowerCase();
            while (rs.next()) {
                //ahmed : contact&ibrahem
                //ibrahem : contact&ahmed
                int port = rs.getInt("port");
                String host = rs.getString("address");
                String username = rs.getString("Username");
                username = username.toLowerCase();
                InetAddress address = InetAddress.getByName(host);
                if (username.equals(sender) || (USER_STATE.containsKey(receiver) && USER_STATE.get(receiver).split("&").length > 1 && USER_STATE.get(sender).split("&")[0].equals(USER_STATE.get(receiver).split("&")[0]) && USER_STATE.get(receiver).split("&")[1].equals(sender))) {
                    byte[] bytes = msg.getBytes();
                    packet = new DatagramPacket(bytes, bytes.length, address, port);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public boolean search_inappropriate_words(Connection con, String receiver, String sender, String msg) {
        String[] res = msg.split(" ");
        for (String word : res) {
            if (INAPPROPRIATE.contains(word)) {
                String stattoensureinsert = "UPDATE `groups` SET `kick`='1' WHERE `grp_name`=? and `member` = ? ";
                try {
                    PreparedStatement preparedStatementinsert = con.prepareStatement(stattoensureinsert);
                    preparedStatementinsert.setString(1, receiver);
                    preparedStatementinsert.setString(2, sender);
                    preparedStatementinsert.executeUpdate();
                    preparedStatementinsert.close();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                return true;
            }
        }
        return false;
    }

    public void one_to_many(Connection con, String sender, String grp_name) {
        String stattoensurelogin = "SELECT * FROM groups_chat as gc WHERE gc.grp_name = ?";
        try {
            PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
            preparedStatement.setString(1, grp_name);
            ResultSet rs = preparedStatement.executeQuery();
            StringBuilder mt = new StringBuilder();
            while (rs.next()) {
                mt.append("\n").append(rs.getString("sender")).append(" : ").append(rs.getString("message"));
            }
            broadcast(con, sender, grp_name, mt.toString());
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void broadcast(Connection con, String sender, String grp_name, String msg) {
        String stattoensurelogin = "SELECT * FROM users as u WHERE u.Username IN( SELECT g.member FROM groups as g WHERE g.grp_name = ? ) and u.port IS NOT NULL";
        try {
            PreparedStatement preparedStatement = con.prepareStatement(stattoensurelogin);
            preparedStatement.setString(1, grp_name);
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                int port = rs.getInt("port");
                String host = rs.getString("address");
                String username = rs.getString("Username");
                username = username.toLowerCase();
                InetAddress address = InetAddress.getByName(host);
                byte[] bytes = msg.getBytes();
                packet = new DatagramPacket(bytes, bytes.length, address, port);
                if (username.equals(sender)) {
                    socket.send(packet);
                } else {

                    if (USER_STATE.containsKey(username) && USER_STATE.get(username).split("&").length > 1 && USER_STATE.get(sender).equals(USER_STATE.get(username))) {
                        socket.send(packet);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
