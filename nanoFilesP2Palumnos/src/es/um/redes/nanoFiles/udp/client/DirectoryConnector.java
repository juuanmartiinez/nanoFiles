package es.um.redes.nanoFiles.udp.client;

import java.awt.desktop.SystemSleepEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Map;

import javax.xml.crypto.Data;

import java.util.LinkedHashMap;
import es.um.redes.nanoFiles.tcp.client.NFConnector;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.udp.message.DirMessage;
import es.um.redes.nanoFiles.udp.message.DirMessageOps;
import es.um.redes.nanoFiles.util.FileDigest;
import es.um.redes.nanoFiles.util.FileInfo;
import es.um.redes.nanoFiles.util.FileNameUtil;

/**
 * Cliente con métodos de consulta y actualización específicos del directorio
 */

public class DirectoryConnector {
	/**
	 * Puerto en el que atienden los servidores de directorio
	 */
	private static final int DIRECTORY_PORT = 6868;
	/**
	 * Tiempo máximo en milisegundos que se esperará a recibir una respuesta por el
	 * socket antes de que se deba lanzar una excepción SocketTimeoutException para
	 * recuperar el control
	 */
	private static final int TIMEOUT = 1000;
	/**
	 * Número de intentos máximos para obtener del directorio una respuesta a una
	 * solicitud enviada. Cada vez que expira el timeout sin recibir respuesta se
	 * cuenta como un intento.
	 */
	private static final int MAX_NUMBER_OF_ATTEMPTS = 5;
	/**
	 * Socket UDP usado para la comunicación con el directorio
	 */
	private DatagramSocket socket;
	/**
	 * Dirección de socket del directorio (IP:puertoUDP)
	 */
	private InetSocketAddress directoryAddress;
	/**
	 * Nombre/IP del host donde se ejecuta el directorio
	 */
	private String directoryHostname;

	public static class DownloadedFile {
		
		public final String filename;
		public final long filesize;
		public final byte[] data;
		public final String filehash;

		public DownloadedFile(String filename, long fsize, byte[] data, String filehash) {
			this.filename = filename;
			this.filesize = fsize;
			this.data = data;
			this.filehash = filehash;
		}
	}

	public DirectoryConnector(String hostname) throws IOException {
		
		directoryHostname = hostname;			// Guardamos el string con el nombre/IP del host

		this.directoryAddress = new InetSocketAddress(InetAddress.getByName(hostname), DIRECTORY_PORT);  // pasamos el hostname a InetAddres y obtenemos la direccion de socket para enviar datagramas a ese destino.

		this.socket = new DatagramSocket();		// crea el socket UDP en cualquier puerto

	}
	/**
	 * Método para enviar y recibir datagramas al/del directorio
	 * 
	 * @param requestData los datos a enviar al directorio (mensaje de solicitud)
	 * @return los datos recibidos del directorio (mensaje de respuesta)
	 */
	private byte[] sendAndReceiveDatagrams(byte[] requestData) {		// implementamos un mecanismo de parada/espera con retransmision para asegurar que los mensajes lleguen aunque no sea un canal confiable.
		
		byte responseData[] = new byte[DirMessage.PACKET_MAX_SIZE];		// buffer de recepcion -> le pasamos el tamaño maximo para evitar desbordamientos
		byte response[] = null;											// inicializamos la respuesta
		
		if (directoryAddress == null) {
			System.err.println("DirectoryConnector.sendAndReceiveDatagrams: UDP server destination address is null!");
			System.err.println(
					"DirectoryConnector.sendAndReceiveDatagrams: make sure constructor initializes field \"directoryAddress\"");
			System.exit(-1);

		}
		if (socket == null) {
			System.err.println("DirectoryConnector.sendAndReceiveDatagrams: UDP socket is null!");
			System.err.println(
					"DirectoryConnector.sendAndReceiveDatagrams: make sure constructor initializes field \"socket\"");
			System.exit(-1);
		}
		
		// metemos los datos en un DatagramPacket y preparamos otro para el envio
		
		DatagramPacket dEnvio = new DatagramPacket(requestData, requestData.length, this.directoryAddress);		
		DatagramPacket dRespuesta = new DatagramPacket(responseData, responseData.length);
		
		int contador = 0;
		boolean recibo = false;
	
		try {
			socket.setSoTimeout(TIMEOUT);								// establecemos cuanto tiempo esperara el socket
			while(contador < MAX_NUMBER_OF_ATTEMPTS && !recibo) {
				try {
					socket.send(dEnvio);
					// System.out.println("Enviando datagrama. Intento: "+ (contador + 1));
					socket.receive(dRespuesta);
					
					recibo = true;
					// System.out.println("Paquete recibido con exito.");
					
					// sin estas dos lineas la comparacion de "pingok" seria imposible, pues seria "ping + basura(ceros)" en el buffer
					
					response = new byte[dRespuesta.getLength()];      	// creo un array del tamaño exacto de los datos que han llegado en realidad
					System.arraycopy(responseData, 0, response, 0, dRespuesta.getLength());	// le da la informacion a response
					
				} catch(SocketTimeoutException e) {						// si se agota el tiempo vamos incrementando el contador	
					contador++;
					System.err.println("Error, no se ha recibido respuesta.");
					if(contador >= MAX_NUMBER_OF_ATTEMPTS){
						System.err.println("Numero maximo de intentos realizados.");
					}
				} 
			}
		} catch (IOException e) {										// capturamos errores criticos
			System.err.println("Error fatal de E/S: ");
			System.exit(1);
		}

		if (response != null && response.length == responseData.length) {
			System.err.println("Your response is as large as the datagram reception buffer!!\n"
					+ "You must extract from the buffer only the bytes that belong to the datagram!");
		}
		return response;						// terminamos devolviendo la respuesta 	
	}
	
	/*
	 * AÑADO UN METODO PARA LA AMPLIACION DE DIRFILES, PUES ES NECESARIO PARA TENER
	 * EN CUENTA EL POSIBLE LISTADO ARBITRARIAMENTE LARGO DE FICHEROS QUE SE PODRIA
	 * DAR
	 */
	
	public byte[] receiveNextDatagram() {
		
		try {
			byte[] responseData = new byte[DirMessage.PACKET_MAX_SIZE];
			DatagramPacket dEnvio = new DatagramPacket(responseData, responseData.length);
			
			socket.receive(dEnvio);
		
			byte[] response = new byte[dEnvio.getLength()];
			System.arraycopy(dEnvio.getData(), 0, response, 0, dEnvio.getLength());
			
			return response;
		}catch (IOException e) {
			return null;
		}
	}
	
	
	/**
	 * Método para probar la comunicación con el directorio mediante el envío y
	 * recepción de mensajes sin formatear ("en crudo")
	 * 
	 * @return verdadero si se ha enviado un datagrama y recibido una respuesta
	 */
	public boolean testSendAndReceive() {

		boolean success = false;

		String miPing = new String("ping");
		
		byte[] bRespuesta = this.sendAndReceiveDatagrams(miPing.getBytes());
		
		if(bRespuesta != null && bRespuesta.length > 0) {        // previene errores como un null, y verifica si recibe respuesta
			
			String miPingOK = new String(bRespuesta).trim();	// .trim() importante para que las comparaciones sean correctas
			
			if(miPingOK.startsWith("pingok")) {
				success = true;
			}
		}
		return success;
	}

	public String getDirectoryHostname() {
		return directoryHostname;
	}
	/**
	 * Método para "hacer ping" al directorio, comprobar que está operativo y que
	 * usa un protocolo compatible. Este método no usa mensajes bien formados.
	 * 
	 * @return Verdadero si
	 */
	public boolean pingDirectoryRaw() {			
		boolean success = false;
		
		try {
			
			String mensaje = "ping&" + NanoFiles.PROTOCOL_ID;				// crear el mensaje a enviar (String "ping&protocolId").
			
			byte[] dataToSend = mensaje.getBytes();							// crea un array de bytes para poder enviarlo
			
			byte[] responseBytes = sendAndReceiveDatagrams(dataToSend); 	// envia los bytes al directorio y espera respuesta
			
			if(responseBytes != null) {										// si la respuesta no es nula, comprobamos si se trata de welcome
				
				String response = new String(responseBytes).trim();			// .trim() fundamental para que la comparacion de strings sea correcta
				
				if(response.equalsIgnoreCase("welcome")){					// si recibimos un welcome
					
					success = true;
					
				}else {
					System.out.println("{pingDirectoryRaw} Error with pingDirectoryRaw");		
				}
			}
			
		} catch(Exception e) {
			System.out.println("{pingDirectoryRaw} Error: " + e.getMessage());
		}

		return success;
	}
	/**
	 * Método para "hacer ping" al directorio, comprobar que está operativo y que es
	 * compatible.
	 * 
	 * @return Verdadero si el directorio está operativo y es compatible
	 */
	public boolean pingDirectory() {
	
		boolean success = false;
		
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_PING);			// definimos la operacion ping
		
		m.setProtocolID(NanoFiles.PROTOCOL_ID);									// añadimos el ID del protocolo 
		
		String message = m.toString();											// pasamos del objeto DirMessage a String		
		byte[] dataToSend = message.getBytes();									// y luego lo pasamos a bytes
		
		byte[] responseBytes = sendAndReceiveDatagrams(dataToSend);				// y se lo pasamos al metodo sendAndReceiveDatagrams que llevara la retransmision para que UDP sea confiable
		
		if(responseBytes != null) {												// en esta parte del codigo nos centramos en la respuesta, donde si no es nulo: 
				
			String responseString = new String(responseBytes);					// pasamos los bytes de respuesta a string
			
			DirMessage responseMsg = DirMessage.fromString(responseString);		// y luego a DirMessage (proceso inverso al que hicimos al principio del metodo)
			
			if(responseMsg != null && responseMsg.getOperation().equals(DirMessageOps.WELCOME)) {
				success = true;
			}
		}
		
		return success;   	
	}
	/**
	 * Método para dar de alta como servidor de ficheros en el puerto indicado.
	 * 
	 * @param serverPort El puerto TCP en el que este peer sirve ficheros a otros
	 * @return Verdadero si el directorio tiene registrado a este peer como servidor
	 *         y acepta la lista de ficheros, falso en caso contrario.
	 */
	public boolean registerFileServer(int serverPort) {											// COMANDO SERVE
		
		boolean success = false;																// variable de control a retornar

	    DirMessage m = new DirMessage(DirMessageOps.OPERATION_REGISTER);						// preparamos el mensaje a enviar, con el comando, nick del peer y puerto
	    m.setNickname(NanoFiles.peerNickname);
	    m.setPort(serverPort);
	    
	    String message = m.toString();															// lo pasamos a String 
	    	    
	    byte[] datoToSend = message.getBytes();													// despues a bytes
	    byte[] responseBytes = sendAndReceiveDatagrams(datoToSend);								// mandamos el mensaje y recibimos la respuesta

	    if(responseBytes != null) {
	    	
	    	String responseString = new String(responseBytes);
	    	DirMessage responseMsg = DirMessage.fromString(responseString);
	    
	    	if(responseMsg != null && responseMsg.getOperation().equals(DirMessageOps.WELCOME)) {		// comprobamos que el Directorio nos haya respondido
				success = true;																			// y retornamos true en caso de que sea cierto
			}
	    	
	    }
	    
		return success;
	}
	
	/**
	 * Método para obtener la lista de ficheros alojados en el directorio. Para cada
	 * fichero se debe obtener un objeto FileInfo con nombre, tamaño y hash.
	 * 
	 * @return Los ficheros disponibles en el directorio, o null si el directorio no
	 *         pudo satisfacer nuestra solicitud
	 */
	
	public FileInfo[] getFileList() {															// DIRFILES AMPLIADO COMPLETAMENTE IMPLEMENTADO
		
		FileInfo[] filelist = new FileInfo[0];													// creamos el objeto a devolver
		
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_DIRFILES);						// creamos el mensaje, donde decimos que queremos una lista de ficheros	
		m.setProtocolID(NanoFiles.PROTOCOL_ID);													// le asinamos el ID del protocolo para poder hablar con el servidor

		String message = m.toString();															// convertimos el objeto mensaje a String
		byte[] dataToSend = message.getBytes();													// y luego a bytes
		
		byte[] responseBytes = sendAndReceiveDatagrams(dataToSend);								// enviamos los bytes y esperamos respuesta segun el metodo sendAndReceiveDatagrams()
		
		StringBuilder allFiles = new StringBuilder();
		
		while(responseBytes != null) {															// CLAVE DE LA AMPLIACION													
			
			String responseString = new String(responseBytes);									// pasamos de bytes a String para luego volver a pasarlo a DirMessage
			DirMessage responseMsg = DirMessage.fromString(responseString);
			
			if(responseMsg != null) {	
				
				allFiles.append(responseMsg.getFiles());
			
				if (responseMsg.getIsLastChunk()) {												// si obtenemos el ultimo chunk de informacion, entonces salimos
	                break;
	            }
				
				responseBytes = receiveNextDatagram();											
			
			}else {
				break;
			}
		}
		
		String files = allFiles.toString().trim();											// extraemos la info de files con el getter

		if(files != null && !files.isEmpty()){												// si no esta vacio o es nulo
			
			try {
				String[] ficheros = files.split(";");
				filelist = new FileInfo[ficheros.length];
				
				for (int i = 0; i < ficheros.length; i++) {
			        String[] partes = ficheros[i].split(":");
			        
			        String nombre = partes[0];
			        long tamano = Long.parseLong(partes[1]);
			        String hash = partes[2];

			        filelist[i] = new FileInfo(hash, nombre, tamano, null);
			    }
			} catch(Exception e) {
				System.err.println("ERROR: " + e.getMessage());
			}
		}
		
		return filelist;																		// devolvemos la lista con toda la info necesaria
	}

	public Map<String, InetSocketAddress> getPeerList() {										// COMADNO: peers 
		Map<String, InetSocketAddress> peers = new LinkedHashMap<String, InetSocketAddress>();
		
		DirMessage message = new DirMessage(DirMessageOps.OPERATION_PEERS);
		
		String messageString = message.toString();
		
		byte[] dataToSend = messageString.getBytes();
		byte[] responseBytes = sendAndReceiveDatagrams(dataToSend);
		
		if(responseBytes != null) {
			
			String response = new String(responseBytes);
			DirMessage responseMsg = DirMessage.fromString(response);
			
			if(responseMsg != null) {
				peers = responseMsg.getPeersList();
			}
		}
		
		return peers;
	}

	public Map<String, InetSocketAddress[]> searchFilesByHash(String hashSubstring) {			// COMANDO: peerdl
		Map<String, InetSocketAddress[]> results = new LinkedHashMap<String, InetSocketAddress[]>();
		return results;
	}

	public DownloadedFile downloadFileFromDirectory(String hashSubstring) {					// DIRDL AMPLIADO
		
		byte[] fileData = new byte[0];
		String filename = null;
		long filesize = -1;
		String filehash = null;
		
		int currentBlock = 0;							// IMPORTANTE: para controlar en que bloque de la transferencia estamos
		int bytesRec = 0;								// IMPORTANTE: indica los bytes recibidos				
		boolean fin = false;							// IMPORTANTE: para controlar hasta cuando iterar(terminaremos cuando lleguemos al ultimo bloque)
		
		while(!fin) {									// controlamos el while con una variable que sera true si y solo si llegamos al final de file que estemos tratando
		
			DirMessage m = new DirMessage(DirMessageOps.OPERATION_DOWNLOAD);
			m.setProtocolID(NanoFiles.PROTOCOL_ID);
			m.setHashSubstring(hashSubstring);
			
			m.setNumSeq(currentBlock);												// IMPORTANTE: indicar el bloque que estamos tratando de la informacion
			
			byte[] responseBytes = sendAndReceiveDatagrams(m.toString().getBytes());
			
			if(responseBytes != null) {
				
				DirMessage responseMsg = DirMessage.fromString(new String(responseBytes));

				if(responseMsg == null) {
				    return null;
				}
				if(responseMsg.getOperation().equals(DirMessageOps.OPERATION_ERROR)) {
				    System.err.println("Directory could not provide a unique file for that hash substring.");
				    return null;
				}
				if(responseMsg.getOperation().equals(DirMessageOps.OPERATION_DOWNLOAD_OK)) {
					
					if(currentBlock == 0) {
						filename = responseMsg.getFileName();
						filesize = responseMsg.getFileSize();
						filehash = responseMsg.getHashSubstring();
						fileData = new byte[(int) filesize];
					}
					
					byte [] chunk = responseMsg.getFileData();
					
					int destinPos = currentBlock * 1024;										// para comprobar y no pasarnos de bytes
					
					if(destinPos + chunk.length <= fileData.length) {							// si no llegamos al total del fichero, seguimos subiendo bloques
						System.arraycopy(chunk, 0, fileData, currentBlock * 1024 , chunk.length);
						bytesRec += chunk.length;
						currentBlock++;
					}else {																		// cuando lleguemos al ultimo, hacemos arraycopy controlando el ultimo byte del file para que no pete
						int actualRemain = fileData.length - destinPos;							
						System.arraycopy(chunk, 0, fileData, destinPos, actualRemain);
						bytesRec += actualRemain;
						fin = true;							// a true pues llegamos al ultimo bloque y tendremos que salir del while
					}			
					if(bytesRec >= filesize) {
						fin = true;
					}	
				}
			}else {
				return null;
			}
			
		}
		
		return new DownloadedFile(filename, filesize, fileData, filehash);
	}
	/**
	 * Método para darse de baja como servidor de ficheros.
	 * 
	 * @return Verdadero si el directorio tiene registrado a este peer como servidor
	 *         y ha dado de baja sus ficheros.
	 */
	public boolean unregisterFileServer() {
		
		boolean success = false;
		
		DirMessage m = new DirMessage(DirMessageOps.OPERATION_QUIT);
		m.setNickname(NanoFiles.peerNickname);
		
		String messageString = m.toString();
		byte[] dataToSend = messageString.getBytes();
		
		byte[] responseBytes = sendAndReceiveDatagrams(dataToSend);
				
		if(responseBytes != null) {
			
			String responseString = new String(responseBytes);
	    	DirMessage responseMsg = DirMessage.fromString(responseString);
	    	
	    	if(responseMsg != null && responseMsg.getOperation().equals(DirMessageOps.OPERATION_QUIT_OK)) {
	    		success = true;
	    	}
			
		}
		
		return success;
	}
}