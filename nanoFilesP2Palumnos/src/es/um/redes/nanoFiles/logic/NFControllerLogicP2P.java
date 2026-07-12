package es.um.redes.nanoFiles.logic;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import es.um.redes.nanoFiles.tcp.client.NFConnector;
import es.um.redes.nanoFiles.tcp.message.PeerMessage;
import es.um.redes.nanoFiles.tcp.message.PeerMessageOps;
import es.um.redes.nanoFiles.application.NanoFiles;



import es.um.redes.nanoFiles.tcp.server.NFServer;
import es.um.redes.nanoFiles.tcp.server.NFServerThread;
import es.um.redes.nanoFiles.udp.server.NFDirectoryServer;
import es.um.redes.nanoFiles.util.FileDigest;
import es.um.redes.nanoFiles.util.FileInfo;
import es.um.redes.nanoFiles.util.FileNameUtil;

public class NFControllerLogicP2P {
	// Servidor TCP local para compartir ficheros con otros peers
	
	private NFServer fileServer = null;
	private int port = 0;
	protected NFControllerLogicP2P() {
	}

	/**
	 * Método para ejecutar un servidor de ficheros en segundo plano. Debe arrancar
	 * el servidor en un nuevo hilo creado a tal efecto.
	 * 
	 * @return Verdadero si se ha arrancado en un nuevo hilo con el servidor de
	 *         ficheros, y está a la escucha en un puerto, falso en caso contrario.
	 * 
	 */
	
	protected boolean startFileServer() {													
		
		boolean serverRunning = false;

		if (fileServer != null) {
			System.err.println("File server is already running");
		} else {
			
			try {
				
				fileServer = new NFServer();
	            
				this.port = fileServer.getPort();
				
				fileServer.startServer(null);

				if(this.port > 0) {
					System.out.println("Server escuchando en puerto: " + port);
					serverRunning = true;
				}
				
			}catch(IOException e) {
				
				System.out.println("Error al iniciar servidor" + e.getMessage());
				fileServer = null;
			}

		}
		return serverRunning;
	}

	protected void testTCPServer() {
		
		assert (NanoFiles.testModeTCP);
		assert (fileServer == null);
		
		try {

			fileServer = new NFServer();
			
			fileServer.test();
			// Este código es inalcanzable: el método 'test' nunca retorna...
		} catch (IOException e1) {
			e1.printStackTrace();
			System.err.println("Cannot start the file server");
			fileServer = null;
		}
		
	}

	public void testTCPClient() {

		assert (NanoFiles.testModeTCP);

		try {
			NFConnector nfConnector = new NFConnector(new InetSocketAddress(NFServer.PORT));
			nfConnector.test();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Método para listar los ficheros de un peer concreto vía TCP e imprimirlos por
	 * pantalla.
	 * 
	 * @param La dirección del peer cuyos ficheros se quiere listar
	 * @return Verdadero si se ha obtenido exitosamente el listado de fichero del
	 *         peer
	 */	
	
	protected boolean listPeerFiles(InetSocketAddress peerAddr) {
	   
		boolean success = false;
	    
	    try{
	    	
	    	Socket socket = new Socket();
	        socket.connect(peerAddr, 3000);

	        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
	        DataInputStream dis = new DataInputStream(socket.getInputStream());
	            
	        PeerMessage req = new PeerMessage(PeerMessageOps.OPCODE_FILE_LIST);
	        
	        req.writeMessageToOutputStream(dos);
	        dos.flush();

	        PeerMessage res = PeerMessage.readMessageFromInputStream(dis);
	            
	        if(res != null && res.getOpcode() == PeerMessageOps.OPCODE_FILE_LIST) {
	        	FileInfo[] files = res.getFileList();
	            if (files != null && files.length > 0) {
	                 FileInfo.printToSysout(files);
	            }else {
	                System.out.println("The peer is not sharing any files.");
	            }
	            	success = true;
	        }
	    
	    } catch (IOException e) {
	    	System.out.println("Error message: " + e.getMessage());
	    }
	    
	    return success;
	}
	
	/**
	 * Descarga un fichero identificado por subcadena de hash desde uno o varios
	 * peers. Si se pasa "*" como nickname, usa el directorio para localizar los
	 * peers que tienen el hash.
	 */
	
	protected boolean downloadFromPeers(NFControllerLogicDir dirLogic, String targetPeerNickname,
	        String targetHashSubstring) {

	    boolean success = false;

	    Map<String, InetSocketAddress> peers = dirLogic.fetchPeerList();
	    
	    if (peers == null || !peers.containsKey(targetPeerNickname)) {
	        System.err.println("Error: Peer " + targetPeerNickname + " not registered.");
	        return success;
	    }

	    success = downloadFileFromServers(new InetSocketAddress[] { peers.get(targetPeerNickname) }, targetHashSubstring);

	    return success;
	}
	
	/**
	 * Método para descargar un fichero del peer servidor de ficheros
	 * 
	 * @param serverAddressList   La lista de direcciones de los servidores a los
	 *                            que se conectará
	 * @param targetHashSubstring Subcadena del hash del fichero a descargar
	 */
	
	protected boolean downloadFileFromServers(InetSocketAddress[] serverAddressList, String targetHashSubstring) {			
		
		if (serverAddressList.length == 0) {
			System.err.println("* Cannot start download - No list of server addresses provided");
			return false;
		}

		final int chunkSize = 8192;

		for (InetSocketAddress serverAddress : serverAddressList) {
			try (Socket socket = new Socket()) {
				socket.connect(serverAddress, 3000);

				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
				DataInputStream dis = new DataInputStream(socket.getInputStream());

				PeerMessage query = new PeerMessage(PeerMessageOps.OPCODE_GET_FILE_HASH);
				query.setHashSubstring(targetHashSubstring);
				query.writeMessageToOutputStream(dos);

				PeerMessage metadata = PeerMessage.readMessageFromInputStream(dis);
				
				if (metadata == null || metadata.getOpcode() == PeerMessageOps.OPCODE_FILE_NOT_FOUND) {
					System.err.println("* The hash substring did not match exactly one file in peer " + serverAddress);
					continue;
				}
				if (metadata.getOpcode() != PeerMessageOps.OPCODE_SEND_FILE) {
					System.err.println("* Unexpected response from peer " + serverAddress);
					continue;
				}

				String remoteName = metadata.getFileName();
				String fullHash = metadata.getHashSubstring();
				long remoteSize = metadata.getFileSize();

				if (remoteName == null || remoteName.isBlank() || fullHash == null || remoteSize < 0) {
					System.err.println("* Peer returned incomplete file metadata");
					continue;
				}

				java.nio.file.Path dest = FileNameUtil.chooseAvailableName(remoteName);

				try (RandomAccessFile raf = new RandomAccessFile(dest.toFile(), "rw")) {
					raf.setLength(remoteSize);

					long offset = 0;
					while (offset < remoteSize) {
						int bytesToRequest = (int) Math.min(chunkSize, remoteSize - offset);

						PeerMessage chunkRequest = new PeerMessage(PeerMessageOps.OPCODE_GET_CHUNCK);
						chunkRequest.setHashSubstring(fullHash);
						chunkRequest.setFileOffset(offset);
						chunkRequest.setChunckSize(bytesToRequest);
						chunkRequest.writeMessageToOutputStream(dos);

						PeerMessage chunkResponse = PeerMessage.readMessageFromInputStream(dis);
						if (chunkResponse == null || chunkResponse.getOpcode() != PeerMessageOps.OPCODE_SEND_FILE) {
							System.err.println("* Error receiving chunk at offset " + offset);
							return false;
						}

						byte[] chunk = chunkResponse.getData();
						if (chunk == null || chunk.length == 0) {
							System.err.println("* Empty chunk received at offset " + offset);
							return false;
						}

						raf.seek(offset);
						raf.write(chunk);
						offset += chunk.length;
					}
				}

				String checksum = FileDigest.computeFileChecksumString(dest.toString());
				if (checksum.equals(fullHash)) {
					System.out.println("* Downloaded peer file to " + toDisplayPath(dest) + " (" + remoteSize + " bytes)");
					System.out.println("* Checksum verified: " + checksum);
					return true;
				} else {
					System.err.println("* WARNING: computed checksum (" + checksum + ") does not match expected hash ("
							+ fullHash + ")");
					return false;
				}

			} catch (IOException e) {
				System.err.println("* Error downloading from peer " + serverAddress + ": " + e.getMessage());
			}
		}

		return false;
	}

	private String toDisplayPath(java.nio.file.Path path) {
		java.nio.file.Path abs = path.toAbsolutePath().normalize();
		java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath().normalize();
		if (abs.startsWith(cwd)) {
			return cwd.relativize(abs).toString();
		}
		return path.toString();
	}

	/**
	 * Método para obtener el puerto de escucha de nuestro servidor de ficheros
	 * 
	 * @return El puerto en el que escucha el servidor, o 0 en caso de error.
	 */
	protected int getServerPort() {										// antes tenia por defecto 0, lo cambiamos para que nos devuelva el puerto correcto
		return this.port;
	}

	/**
	 * Método para detener nuestro servidor de ficheros en segundo plano
	 * 
	 */
	
	protected void stopFileServer() {									// COMANDO: quit	
		
		if(fileServer != null) {
			fileServer = null;
			System.exit(0);
			
		}
	}

	protected boolean serving() {
		boolean result = false;
		
		if(fileServer != null) {
			result = true;
		}
		return result;
	}

}
