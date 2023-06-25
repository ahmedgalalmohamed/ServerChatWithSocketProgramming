package org.example;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
public class Server {
    public static void main(String[] args) throws Exception{
        DatagramSocket socket = new DatagramSocket(1236);
        DatagramPacket packet;
        System.out.println("Server Listen " + 1236 + " :");
        while (true) {
            byte[] buffer = new byte[1024];
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            MainThread th = new MainThread(socket, packet, buffer);
            th.start();
        }
    }
}