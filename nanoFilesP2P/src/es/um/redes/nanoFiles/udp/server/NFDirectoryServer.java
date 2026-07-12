package es.um.redes.nanoFiles.udp.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedHashMap;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.logic.NFControllerLogicP2P;
import es.um.redes.nanoFiles.udp.message.DirMessage;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileInfo;
import es.um.redes.nanoFiles.util.NickGenerator;

public class NFDirectoryServer {
	/**
	 * Número de puerto UDP en el que escucha el directorio
	 */
	public static final int DIRECTORY_PORT = 6868;

	/**
	 * Socket de comunicación UDP con el cliente UDP (DirectoryConnector)
	 */
	private DatagramSocket socket = null;
	
	/**
	 * Lista de ficheros alojados en el directorio.
	 */
	private FileInfo[] directoryFiles;
	/**
	 * Lista de servidores registrados (IP, puerto TCP).
	 */
	private LinkedHashMap<String, InetSocketAddress> registeredPeers;

	/**
	 * Probabilidad de descartar un mensaje recibido en el directorio (para simular
	 * enlace no confiable y testear el código de retransmisión)
	 */
	private double messageDiscardProbability;

	public NFDirectoryServer(double corruptionProbability, String directoryFilesPath) throws SocketException {		// FALTA POR COMPLETAR?¿=??¿?¿
		/*
		 * Guardar la probabilidad de pérdida de datagramas (simular enlace no
		 * confiable)
		 */
		messageDiscardProbability = corruptionProbability;
		/*
		 * Cargar los ficheros del directorio compartido.
		 */
		File dir = new File(directoryFilesPath);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		directoryFiles = FileInfo.loadFilesFromFolder(directoryFilesPath);
		System.out.println("* Directory loaded " + directoryFiles.length + " files from " + directoryFilesPath);
			
       	this.socket = new DatagramSocket(DIRECTORY_PORT);								// creamos un socket UDP con el puerto de DIRECTORY_PORT

 		this.registeredPeers = new LinkedHashMap<String, InetSocketAddress>();    		// revisar en caso de que el servidor sea multihilo
		
		if (NanoFiles.testModeUDP) {
			if (socket == null) {
				System.err.println("[testMode] NFDirectoryServer: code not yet fully functional.\n"
						+ "Check that all TODOs in its constructor and 'run' methods have been correctly addressed!");
				System.exit(-1);
			}
		}
	}

	public DatagramPacket receiveDatagram() throws IOException {
		
		DatagramPacket datagramReceivedFromClient = null;
		boolean datagramReceived = false;
		while (!datagramReceived) {
			
			byte[] buffer = new byte[DirMessage.PACKET_MAX_SIZE];						// creamos un buffer para recibir datagramas
			datagramReceivedFromClient = new DatagramPacket(buffer, buffer.length);		// datagrama asociado al buffer 
			
			this.socket.receive(datagramReceivedFromClient);							// recibimos un datagrama a traves del socket
						
			if (datagramReceivedFromClient == null) {
				System.err.println("[testMode] NFDirectoryServer.receiveDatagram: code not yet fully functional.\n"
						+ "Check that all TODOs have been correctly addressed!");
				System.exit(-1);
			} else {
				// Vemos si el mensaje debe ser ignorado (simulación de un canal no confiable)
				double rand = Math.random();
				if (rand < messageDiscardProbability) {
					System.err.println(
							"Directory ignored datagram from " + datagramReceivedFromClient.getSocketAddress());
				} else {
					datagramReceived = true;
				}
			}

		}

		return datagramReceivedFromClient;
	}

	public void runTest() throws IOException {

		System.out.println("[testMode] Directory starting...");

		System.out.println("[testMode] Attempting to receive 'ping' message...");
		DatagramPacket rcvDatagram = receiveDatagram();
		sendResponseTestMode(rcvDatagram);
		
		System.out.println("[testMode] Attempting to receive 'ping&PROTOCOL_ID' message...");
		rcvDatagram = receiveDatagram();
		sendResponseTestMode(rcvDatagram);
	}

	private void sendResponseTestMode(DatagramPacket pkt) throws IOException { 	// BOLETIN 3
			
		String cadena = new String(pkt.getData(), 0, pkt.getLength()).trim();				// obtenemos los bytes del datagrama que nos pasan
		System.out.println("Cadena recibida: "+ cadena);
		
		String respuesta = "invalid";
		
		if(cadena.equals("ping")) {															// caso simple de ping, donde respondemos en ese caso con -> pingok
			
			respuesta = "pingok";
			
		}else if(cadena.startsWith("ping&")) {												// en el caso de que el ping que recibamos no sea un simple 'ping'
			
			String protocoloId = cadena.substring(5);										// extraemos la parte del ID del ping&ID
			
			if(protocoloId.equals(NanoFiles.PROTOCOL_ID)) {									// comprobamos si la ID del protocolo del cliente coincide con la del servidor y actualizamos la respuesta segun el caso
				respuesta = "welcome";					
			}else {
				respuesta = "denied";
			}
		}

		DatagramPacket dRespuesta = new DatagramPacket(respuesta.getBytes(), 				// creamos un datagrama con la respuesta decidida anteriormente
				respuesta.getBytes().length, pkt.getSocketAddress());
		
		this.socket.send(dRespuesta);														// lo enviamos
		
		String messageFromClient = new String(pkt.getData(), 0, pkt.getLength());			// depuracion 
		System.out.println("Data received: " + messageFromClient);
		
	}

	public void run() throws IOException {

		System.out.println("Directory starting...");

		while (true) { // Bucle principal del servidor de directorio
			DatagramPacket rcvDatagram = receiveDatagram();

			sendResponse(rcvDatagram);

		}
	}

	private void sendResponse(DatagramPacket pkt) throws IOException {

		byte[] dataReceived = pkt.getData();      											// datos recibidos del datagrama pkt
		String dataReceivedString = new String(dataReceived, 0, pkt.getLength());			// lo pasamos a string

		// System.out.println(dataReceivedString);											// imprime el BLOQUE -> CLAVE:VALOR
		
		DirMessage m = DirMessage.fromString(dataReceivedString);							// lo pasamos de String a DirMessage
		
		String operation = m.getOperation();								
		
		DirMessage mToSend = null;									

		System.out.println("Received datagram from " + pkt.getAddress() + ":" + pkt.getPort() + 		// imprime como el video ejemplo del profesor
                " - operation=" + operation);
		
		switch (operation) {
		case DirMessageOps.OPERATION_PING: 												// CASO PARA PING
			
			if(m.getProtocolId().equals(NanoFiles.PROTOCOL_ID)) {						// en el caso de que coincida el protocolo, enviamos el WELCOME
				mToSend = new DirMessage(DirMessageOps.WELCOME);
			}else {
				mToSend = new DirMessage(DirMessageOps.OPERATION_ERROR);
			}
			break;
		case DirMessageOps.OPERATION_DIRFILES:											// CASO PARA DIRFILES 
			
			if(m.getProtocolId().equals(NanoFiles.PROTOCOL_ID)) {
				
				mToSend = new DirMessage(DirMessageOps.OPERATION_DIRFILES_OK);
				
				String data = new String();
				
				for (FileInfo f : directoryFiles) {
					data  = data + f.fileName + ":" + f.fileSize + ":" + f.fileHash + ";";
			    }		
				
				String fullData = data.toString();
				
				if(fullData.length() < DirMessage.PACKET_MAX_SIZE - 200) {				// IMPLEMENTACION DIRFILES AMPLIADO
					mToSend.setFiles(fullData);
					mToSend.setLastChunk(true);
				}else {
					
					int offset = 0;
					
					while(offset < fullData.length()) {

						int length = Math.min(DirMessage.PACKET_MAX_SIZE - 200, fullData.length() - offset);
						
						byte[] chunk = new byte[length];
						
						System.arraycopy(fullData.getBytes(), offset, chunk, 0, length);
						
						DirMessage mChunk = new DirMessage(DirMessageOps.OPERATION_DIRFILES_OK);
						mChunk.setFiles(new String(chunk));
						
						boolean last = (offset + length >= fullData.length());
						mChunk.setLastChunk(last);
						
			            byte[] dataSend = mChunk.toString().getBytes();
			            DatagramPacket response = new DatagramPacket(dataSend, dataSend.length, pkt.getAddress(), pkt.getPort());
			            
			            socket.send(response);
			            
						offset += length;
					}
					return;
				}
				
			}else {
				mToSend = new DirMessage(DirMessageOps.OPERATION_ERROR);
			}
			
			break;

		case DirMessageOps.OPERATION_DOWNLOAD:											// CASO PARA DIRDL (para dirdl ampliado usamos Parada y Espera con enumeracion de secuencias)
		
			if(m.getProtocolId().equals(NanoFiles.PROTOCOL_ID)) {						// IMPORTANTE -> falta indicar ambigüedad
				
				String hashSub = m.getHashSubstring();
				int blockReq = m.getNumSeq();											// obtenemos el numero de bloque solicitado por el cliente
				int chunkSize = 1024;
				FileInfo[] cont = FileInfo.lookupHashSubstring(directoryFiles, hashSub);	
				
				if(cont.length == 1) {
					mToSend = new DirMessage(DirMessageOps.OPERATION_DOWNLOAD_OK);
					mToSend.setFileName(cont[0].fileName);
					
					long totalSize = new java.io.File(cont[0].filePath).length();		// tamaño real para que el cliente sepa cuanto debe recibir en total
		            mToSend.setFileSize(totalSize);
		            
					mToSend.setHashSubstring(cont[0].fileHash);
					
					// IMPORTANTE: usamos RandomAccesFile para descargar ficheros de cualquier tamaño
					
					try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(cont[0].filePath, "r")){
						
			             raf.seek((long) blockReq * chunkSize);							// apunta el puntero al sitio x del fichero
			             
			             long remain = totalSize - (long) blockReq * chunkSize;			// nos dice cuantos bytes nos quedan desde donde estamos hasta el final del size del fichero
			             int read = (int) Math.min(chunkSize, remain);					// leemos el tamaño del fragmento
			             
			             byte[] buffer = new byte[read];
			             raf.readFully(buffer);											// leemos solo los bytes que necesitamos
			             mToSend.setFileData(buffer);
			             mToSend.setNumSeq(blockReq);
		             
		            } catch (java.io.IOException e) {
		                System.err.println("Error al leer el archivo: " + e.getMessage());
		            }
				}else {
					mToSend = new DirMessage(DirMessageOps.OPERATION_ERROR);
					if (cont.length == 0) {
					    System.err.println("Hash provided was not found.");
					} else {
					    System.err.println("Hash provided is ambiguous.");
					}			
				}
				
			}else {
				mToSend = new DirMessage(DirMessageOps.OPERATION_ERROR);
			}
			
			break;

		case DirMessageOps.OPERATION_REGISTER:											// COMANDO SERVE 
						
			String nicknameServe = m.getNickname();
		    int portServe = m.getPort(); 
		    InetAddress ipServe = pkt.getAddress();
		    
		    if (nicknameServe != null && !nicknameServe.isEmpty()) {
		        registeredPeers.put(nicknameServe, new InetSocketAddress(ipServe, portServe));
		        mToSend = new DirMessage(DirMessageOps.WELCOME); 
		        System.out.println("Peer registered: " + nicknameServe + " @ " + ipServe + ":" + portServe);
		    } else {
		        mToSend = new DirMessage(DirMessageOps.OPERATION_ERROR);
		    }
		    break;
		    
		case DirMessageOps.OPERATION_PEERS:												// OPERATION: peers
			
			mToSend = new DirMessage(DirMessageOps.OPERATION_PEERS_OK);
			
			mToSend.setPeersList(registeredPeers);
			
			break;
		
		case DirMessageOps.OPERATION_QUIT:
			
			String nicknameQuit = m.getNickname();
			
			if(nicknameQuit != null) {
				
				mToSend = new DirMessage(DirMessageOps.OPERATION_QUIT_OK);
			
				registeredPeers.remove(nicknameQuit);
				
			}
			
			break;
			
		default:
			System.err.println("Unexpected message operation: \"" + operation + "\"");
			System.exit(-1);
		}

		
		String mToSendString = mToSend.toString();									// lo pasamos a String
		byte[] dataSend = mToSendString.getBytes();									// y posteriormente a byte[] para contruir el DatagramPacket
		
		DatagramPacket response = new DatagramPacket(dataSend, dataSend.length , pkt.getAddress(), pkt.getPort());		// respuesta que enviaremos como resultado del metodo
		
		System.out.println("Send response to operation " + operation + " to " + pkt.getAddress() + 		// imprime como el video ejemplo del profesor
                ":" + pkt.getPort());
		
		socket.send(response);														// enviamos la respuesta
	}	


}
