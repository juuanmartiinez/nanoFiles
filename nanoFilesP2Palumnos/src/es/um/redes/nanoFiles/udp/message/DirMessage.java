package es.um.redes.nanoFiles.udp.message;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

/**
 * Clase que modela los mensajes del protocolo de comunicación entre pares para
 * implementar el explorador de ficheros remoto (servidor de ficheros). Estos
 * mensajes son intercambiados entre las clases DirectoryServer y
 * DirectoryConnector, y se codifican como texto en formato "campo:valor".
 * 
 * @author rtitos
 *
 */
public class DirMessage {
	
	public static final int PACKET_MAX_SIZE = 65507; // 65535 - 8 (UDP header) - 20 (IP header)

	private static final char DELIMITER = ':'; // Define el delimitador
	private static final char END_LINE = '\n'; // Define el carácter de fin de línea

	/**
	 * Nombre del campo que define el tipo de mensaje (primera línea)
	 */
	
	private static final String FIELDNAME_OPERATION = "operation";
	
	/*
	 * TODO: (Boletín MensajesASCII) Definir de manera simbólica los nombres de
	 * todos los campos que pueden aparecer en los mensajes de este protocolo
	 * (formato campo:valor)
	 * ---------------HECHO------------------
	 */
	
	private static final String FIELDNAME_PROTOCOLID = "protocol";
	private static final String FIELDNAME_NICKNAME   = "nickname";
	private static final String FIELDNAME_PORT       = "port";
	private static final String FIELDNAME_FILELIST   = "files";
	private static final String FIELDNAME_STATUS     = "status";
	private static final String FIELDNAME_HASH 		 = "hash";
	private static final String FIELDNAME_FILENAME   = "filename";
	private static final String FIELDNAME_FILESIZE   = "filesize";
	private static final String FIELDNAME_DATA       = "data";
	private static final String FIELDNAME_PEERLIST   = "peerlist";
	private static final String FIELDNAME_LASTCHUNK  = "lastchunk";
	private static final String FIELDNAME_NUMSEQ  = "numseq";
	/**
	 * Tipo del mensaje, de entre los tipos definidos en PeerMessageOps.
	 */
	private String operation = DirMessageOps.OPERATION_INVALID;
	/**
	 * Identificador de protocolo usado, para comprobar compatibilidad del directorio.
	 */
	private String protocolId;
	private String hashSubstring;    				// AÑADIDO PARA dirdl
	private String filename;						// AÑADIDO PARA dirdl
	private long fileSize = -1;						// AÑADIDO PARA dirdl
	private byte[] fileData;						// AÑADIDO PARA dirdl
	private String filepath;						// AÑADIDO PARA dirdl
	private int numSeq = -1;								// AÑADIDO PARA dirdl AMPLIADO
	private Map<String, InetSocketAddress> peerList = new LinkedHashMap<>();    // AÑADIDO PARA PEERS

	private String nickname;
	private int port;
	private String files;
	private String status;
	private boolean last;										

	public DirMessage(String op) {
		operation = op;
	}
	
	public String getFilePath() {
		return filepath;
	}
	
	public String getNickname() {
		return nickname;
	}
	
	public int getPort() {
		return port;
	}
	
	public String getFiles() {
		return files;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getOperation() {
		return operation;
	}

	public String getHashSubstring() {
		return hashSubstring;
	}
	
	public String getFileName() {
		return filename;
	}
	
	public long getFileSize() {
		return fileSize;
	}
	
	public byte[] getFileData() {
		return fileData;
	}
	
	public boolean getIsLastChunk() {
		return last;
	}
	
	public int getNumSeq() {
		return numSeq;
	}
	
	public Map<String, InetSocketAddress> getPeersList(){
		return peerList;
	}
	
	public void setPeersList(Map<String, InetSocketAddress> p) {
		peerList = p;
	}
	
	public void setNumSeq(int n) {
		numSeq = n;
	}
	
	public void setFilePath(String fileP) {
		filepath = fileP;
	}
	
	public void setFileData(byte[] data) {
	    fileData = data;
	}
	
	public void setHashSubstring(String hashSub) {
		hashSubstring = hashSub;
	}
	
	public void setProtocolID(String protocolIdent) {
		if (!operation.equals(DirMessageOps.OPERATION_PING) && !operation.equals(DirMessageOps.OPERATION_DIRFILES)
				&& !operation.equals(DirMessageOps.OPERATION_DOWNLOAD)) {
			throw new RuntimeException(
					"DirMessage: setProtocolId called for message of unexpected type (" + operation + ")");
		}
		protocolId = protocolIdent;
	}
	
	public void setNickname(String nick) {
		if(!operation.equals(DirMessageOps.OPERATION_REGISTER) && !operation.equals(DirMessageOps.OPERATION_QUIT)) {
			throw new RuntimeException(
					"DirMessage: setNickname called for message of unexpected type (" + operation + ")");
		}
		nickname = nick;
	}
	
	public void setPort(int p) {
		if(!operation.equals(DirMessageOps.OPERATION_REGISTER)) {
			throw new RuntimeException(
					"DirMessage: setPort called for message of unexpected type (" + operation + ")");
		}
		this.port = p;
	}
	
	public void setFiles(String f) {
		if(!operation.equals(DirMessageOps.OPERATION_DIRFILES) && !operation.equals(DirMessageOps.OPERATION_DIRFILES_OK)) {
			throw new RuntimeException(
					"DirMessage: setFiles called for message of unexpected type (" + operation + ")");
		}
		files = f;
	}

	public void setLastChunk(boolean l) {						// AMPLIACION DIRFILES
		last = l;
	}
	
	public void setStatus(String st) {
		status = st;
	}
	
	public String getProtocolId() {

		return protocolId;
	}
	
	public void setFileSize(long size) {
		this.fileSize = size;
	}
	
	public void setFileName(String name) {
		filename = name;
	}

	/**
	 * Método que convierte un mensaje codificado como una cadena de caracteres, a
	 * un objeto de la clase PeerMessage, en el cual los atributos correspondientes
	 * han sido establecidos con el valor de los campos del mensaje.
	 * 
	 * @param message El mensaje recibido por el socket, como cadena de caracteres
	 * @return Un objeto PeerMessage que modela el mensaje recibido (tipo, valores,
	 *         etc.)
	 */
	
	public static DirMessage fromString(String message) {
		
		String[] lines = message.split(END_LINE + "");
		DirMessage m = null;

		for (String line : lines) {	
			
			if (line.trim().isEmpty()) continue; // Ignorar líneas vacías
			
			int idx = line.indexOf(DELIMITER); 							// Posición del delimitador
			String fieldName = line.substring(0, idx).toLowerCase(); 	// minúsculas
			String value = line.substring(idx + 1).trim();

			switch (fieldName) {										
			
			case FIELDNAME_OPERATION: {
				assert (m == null);
				m = new DirMessage(value);
				break;
			}
			case FIELDNAME_PROTOCOLID: {
				if(m != null) m.protocolId = value;
				break;
			}
			case FIELDNAME_NICKNAME: {
				if(m != null) m.nickname = value;
				break;
			}
			case FIELDNAME_PORT: {
				if(m != null) m.port = Integer.parseInt(value);
				break;
			}
			case FIELDNAME_FILELIST: {
				if(m != null) m.files = value;
				break;
			}
			case FIELDNAME_STATUS: {
				if(m != null) m.status = value;
				break;
			}
			case FIELDNAME_HASH:{
				if(m != null) m.hashSubstring = value;
				break;
			}
			case FIELDNAME_FILENAME:{
				if(m != null) m.filename = value;
				break;
			}
			case FIELDNAME_FILESIZE:{
				if(m != null) m.fileSize = Long.parseLong(value);
				break;
			}
			case FIELDNAME_DATA:{
				if(m != null) m.fileData = java.util.Base64.getDecoder().decode(value);
				break;
			}
			
			case FIELDNAME_PEERLIST:{
			    if (m != null) {

			    	String[] parts = value.split("@");
			        if (parts.length == 2) {
			            String nick = parts[0];
			            String[] addr = parts[1].split(":");
			            if (addr.length == 2) {
			                m.peerList.put(nick, new InetSocketAddress(addr[0], Integer.parseInt(addr[1])));
			            }
			        }
			    }
			    break;
			}
			
			case FIELDNAME_LASTCHUNK:
				if(m != null) m.last = Boolean.parseBoolean(value);
				break;
				
			case FIELDNAME_NUMSEQ:
				if(m != null) m.numSeq = Integer.parseInt(value);
				break;
			
			default:
				System.err.println("PANIC: DirMessage.fromString - message with unknown field name " + fieldName);
				System.err.println("Message was:\n" + message);
				System.exit(-1);
			}
		}

		return m;
	}

	/**
	 * Método que devuelve una cadena de caracteres con la codificación del mensaje
	 * según el formato campo:valor, a partir del tipo y los valores almacenados en
	 * los atributos.
	 * 
	 * @return La cadena de caracteres con el mensaje a enviar por el socket.
	 */
	
	public String toString() {																

		StringBuffer sb = new StringBuffer();
		sb.append(FIELDNAME_OPERATION + DELIMITER + operation.trim() + END_LINE); 					// Construimos el campo
		
		// creamos una cadena con los campos necesarios segun la operacio de mensaje
		
		if(protocolId != null) {
			sb.append(FIELDNAME_PROTOCOLID + DELIMITER + protocolId.trim() + END_LINE);
		}
		
		if(files != null) {
			sb.append(FIELDNAME_FILELIST + DELIMITER + files + END_LINE);
		}
		
		if(nickname != null) {
			sb.append(FIELDNAME_NICKNAME + DELIMITER + nickname.trim() + END_LINE);
		}
		
		if(hashSubstring != null) {
	        sb.append(FIELDNAME_HASH + DELIMITER + hashSubstring.trim() + END_LINE);
	    }
		
		if(status != null) {
			sb.append(FIELDNAME_STATUS + DELIMITER + status.trim() + END_LINE);
		}
		
		if(port > 0) {
			sb.append(FIELDNAME_PORT + DELIMITER + port + END_LINE);
		}
		
		if (filename != null) {
		    sb.append(FIELDNAME_FILENAME + DELIMITER + filename + END_LINE);
		}
		if(fileSize >= 0) {
			sb.append(FIELDNAME_FILESIZE + DELIMITER + fileSize + END_LINE);
		}
		
		if (fileData != null) {
		    String encoded = java.util.Base64.getEncoder().encodeToString(fileData);
		    sb.append("data" + DELIMITER + encoded + END_LINE);
		}
		
		if (this.peerList != null && !this.peerList.isEmpty()) {
		    for (Map.Entry<String, InetSocketAddress> entry : peerList.entrySet()) {

		    	sb.append(FIELDNAME_PEERLIST + DELIMITER + entry.getKey() + "@" + 
		                  entry.getValue().getAddress().getHostAddress() + ":" + 
		                  entry.getValue().getPort() + END_LINE);
		    }
		}
		
		if(operation.equals(DirMessageOps.OPERATION_DIRFILES) || operation.equals(DirMessageOps.OPERATION_DIRFILES_OK)) {
			sb.append(FIELDNAME_LASTCHUNK + DELIMITER + last + END_LINE);
		}
			
		if(numSeq >= 0) {
			sb.append(FIELDNAME_NUMSEQ + DELIMITER + numSeq + END_LINE);
		}
		
		return sb.toString();
	}

}
