package es.um.redes.nanoFiles.tcp.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;

public class NFServer implements Runnable {

	public static final int PORT = 10000;

	private ServerSocket serverSocket = new ServerSocket();

	public NFServer() throws IOException {		
		
		InetSocketAddress address = new InetSocketAddress(0); 			// Crear una direción de socket a partir del puerto especificando (PORT)
		serverSocket.bind(address);										// lo liga a la direccion de socket anterior
		
	}

	/**
	 * Método para ejecutar el servidor de ficheros en primer plano. Sólo es capaz
	 * de atender una conexión de un cliente. Una vez se lanza, ya no es posible
	 * interactuar con la aplicación.
	 * 
	 */
	
	public void test() {
		
		if (serverSocket == null || !serverSocket.isBound()) {
			System.err.println(
					"[fileServerTestMode] Failed to run file server, server socket is null or not bound to any port");
			return;
		} else {
			System.out
					.println("[fileServerTestMode] NFServer running on " + serverSocket.getLocalSocketAddress() + ".");
		}

		while (true) {
			
			try {
				
				Socket clientSocket = serverSocket.accept();							// espera conexiones de otros peers
				serveFilesToClient(clientSocket);										
					
			} catch (IOException e) {
				System.out.println("Error en la conexion:" + e.getMessage());
			}
		}
	}

	/**
	 * Método que ejecuta el hilo principal del servidor en segundo plano, esperando
	 * conexiones de clientes.
	 * 
	 * @see java.lang.Runnable#run()
	 */
	
	public void run() {
		
		while (true) {
			
			try {
					
				Socket clientSocket = serverSocket.accept();							// espera conexiones de otros peers
				clientSocket.setSoTimeout(2000);
				NFServerThread thread = new NFServerThread(clientSocket);
				
				thread.start();
					
			} catch (IOException e) {
				System.out.println("Error en la conexion:" + e.getMessage());
			}
		}
		
	}
	
	public void startServer(ServerSocket s) {
		
		Thread serverT = new Thread(this);
		serverT.start();
	}
	
	public void stopServer() {
		
		try {
			serverSocket.close();
		}catch(IOException e) {
			System.out.println("");
		}
	}
	
	public int getPort() {										// -> CLAVE, antes no devolvia el socket correcto y se quedaba colgada la terminal
		return serverSocket.getLocalPort();
	}
	
	/**
	 * Método de clase que implementa el extremo del servidor del protocolo de
	 * transferencia de ficheros entre pares.
	 * 
	 * @param socket El socket para la comunicación con un cliente que desea
	 *               descargar ficheros.
	 */

	public static void serveFilesToClient(Socket socket) {													
		
		DataInputStream dis = null;
		DataOutputStream dos = null;
		
		try {
			
			dis = new DataInputStream(socket.getInputStream());
	        dos = new DataOutputStream(socket.getOutputStream());
	        
		while(true) {
			
			try {
				
				PeerMessage message = new PeerMessage();
				message = PeerMessage.readMessageFromInputStream(dis);
				
				if (message == null) break;
				
				byte opcode = message.getOpcode();
				
				switch (opcode) {
					case PeerMessageOps.OPCODE_INVALID_CODE: 	
					
						break;
						
					case PeerMessageOps.OPCODE_FILE_NOT_FOUND:
						
						break;
						
					case PeerMessageOps.OPCODE_GET_CHUNCK:
						
						String hash = message.getHashSubstring();
						long offset = message.getFileOffset();
						int size = message.getChuckSize();
						
						String path = es.um.redes.nanoFiles.application.NanoFiles.db.lookupFilePath(hash);
						
						if(path != null) {
							
							
							File fileOnDisk = new File(path);
					        long fileSize = fileOnDisk.length();
							
							int bytesRead = size;
							
							if (offset + size > fileSize) {
					            bytesRead = (int) (fileSize - offset);
					        }
							
							if (bytesRead > 0) {
					            try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
					                raf.seek(offset);
					                
					                byte[] fileBuffer = new byte[bytesRead];
					                raf.readFully(fileBuffer);


									PeerMessage responseChunk = new PeerMessage(PeerMessageOps.OPCODE_SEND_FILE);
									responseChunk.setHashSubstring(hash);
									
									FileInfo[] match = FileInfo.lookupHashSubstring(
									        es.um.redes.nanoFiles.application.NanoFiles.db.getFiles(), hash);
									
									if (match.length == 1) {
									    responseChunk.setFileName(match[0].fileName);
									    responseChunk.setFileSize(match[0].fileSize);
									} else {
									    responseChunk.setFileName(fileOnDisk.getName());
									    responseChunk.setFileSize(fileSize);
									}
									
									responseChunk.setFileOffset(offset);
									responseChunk.setData(fileBuffer);
									
									responseChunk.writeMessageToOutputStream(dos);
									
					            } catch (IOException e) {
					                PeerMessage error = new PeerMessage(PeerMessageOps.OPCODE_FILE_NOT_FOUND);
					                error.writeMessageToOutputStream(dos);
					            }
					        }
							
						}
						
						break;
						
					case PeerMessageOps.OPCODE_FILE_LIST:
						
						FileInfo[] files = es.um.redes.nanoFiles.application.NanoFiles.db.getFiles();
						
						PeerMessage response = new PeerMessage(PeerMessageOps.OPCODE_FILE_LIST);
						response.setFileList(files);
												
						response.writeMessageToOutputStream(dos);
						
						socket.close();
						
						return;							// necesario, sino la terminal se queda pillada
					
					case PeerMessageOps.OPCODE_SEND_FILE:
						
						String hashFile = message.getHashSubstring();
						
						PeerMessage sendFile = new PeerMessage(PeerMessageOps.OPCODE_SEND_FILE);
						
						sendFile.setHashSubstring(hashFile);
						
						sendFile.writeMessageToOutputStream(dos);
						
						break;
					
					case PeerMessageOps.OPCODE_GET_FILE_HASH:
						
						String hashSubString = message.getHashSubstring();
						
						FileInfo[] f = es.um.redes.nanoFiles.application.NanoFiles.db.getFiles();
						
						FileInfo[] match = FileInfo.lookupHashSubstring(f, hashSubString);
						
						if (match.length == 1) {
						    response = new PeerMessage(PeerMessageOps.OPCODE_SEND_FILE);
						    response.setHashSubstring(match[0].fileHash);
						    response.setFileName(match[0].fileName);
						    response.setFileSize(match[0].fileSize);
						    response.setFileOffset(0);
						    response.setData(new byte[0]);
						} else {
						    response = new PeerMessage(PeerMessageOps.OPCODE_FILE_NOT_FOUND);
						}
						response.writeMessageToOutputStream(dos);
						
						break;
						
					default:
					
						new PeerMessage(PeerMessageOps.OPCODE_FILE_NOT_FOUND).writeMessageToOutputStream(dos);
						
			 }
				
				
			}catch (EOFException e) {
			    break; 
			}catch (IOException e) {
				break;
			}
			
		}
		
		}catch (IOException e) {
				// TODO: handle exception
		}
		
	}
	
	
	
	
}


