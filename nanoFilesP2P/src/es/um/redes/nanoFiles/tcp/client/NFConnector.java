package es.um.redes.nanoFiles.tcp.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.tcp.server.NFServer;
import es.um.redes.nanoFiles.util.FileInfo;

//Esta clase proporciona la funcionalidad necesaria para intercambiar mensajes entre el cliente y el servidor

public class NFConnector {
	
	private Socket socket;
	private InetSocketAddress serverAddr;

	public NFConnector(InetSocketAddress fserverAddr) throws UnknownHostException, IOException {			
		serverAddr = fserverAddr;
		
		socket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
		
		DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
		DataInputStream dis = new DataInputStream(socket.getInputStream());
		
	}

	public void test() {																
		
	}
	
	public InetSocketAddress getServerAddr() {
		return serverAddr;
	}

}
