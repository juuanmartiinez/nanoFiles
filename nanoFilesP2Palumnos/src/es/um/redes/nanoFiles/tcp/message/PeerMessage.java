package es.um.redes.nanoFiles.tcp.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import es.um.redes.nanoFiles.application.NanoFiles;
import es.um.redes.nanoFiles.util.FileInfo;

public class PeerMessage {

	private byte opcode;


	// ATRIBUTOS NECESARIOS
	
	private FileInfo[] fileList; 		// AÑADIDO PARA -> peerfiles
	private long fileOffset;			// AÑADIDO
	private int chunkSize;				// AÑADIDO
	private String hashSubstring;		// AÑADIDO
	private byte[] data;				// AÑADIDO
	private String fileName;			// AÑADIDO
	private long fileSize = -1;			// AÑADIDO
	
	public PeerMessage() {
		this.opcode = PeerMessageOps.OPCODE_INVALID_CODE;
	}

	public PeerMessage(byte op) {
		opcode = op;
	}

	// GETTER Y SETTERS
	
	public String getFileName() {
		return fileName;
	}
	
	public long getFileSize() {
		return fileSize;
	}
	
	public void setFileName(String name) {
		fileName = name;
	}
	
	public void setFileSize(long size) {
		fileSize = size;
	}
	
	public byte getOpcode() {
		return opcode;
	}
	
	public void setOpcode(byte opC) {
		opcode = opC;
	}
	
	public FileInfo[] getFileList() {
		return fileList;
	}
	
	public void setFileList(FileInfo[] fList) {
		fileList = fList;
	}
	
	public long getFileOffset() {
		return fileOffset;
	}
	
	public void setFileOffset(long fileOf) {
		fileOffset = fileOf;
	}
	
	public int getChuckSize() {
		return chunkSize;
	}
	
	public void setChunckSize(int chunckS) {
		chunkSize = chunckS;
	}

	public String getHashSubstring() {
		return hashSubstring;
	}
	
	public void setHashSubstring(String hashSub) {
		hashSubstring = hashSub;
	}
	
	public byte[] getData() {
		return data;
	}
	
	public void setData(byte[] d) {
		data = d;
	}
	
	/**
	 * Método de clase para parsear los campos de un mensaje y construir el objeto
	 * DirMessage que contiene los datos del mensaje recibido
	 * 
	 * @param data El array de bytes recibido
	 * @return Un objeto de esta clase cuyos atributos contienen los datos del
	 *         mensaje recibido.
	 * @throws IOException
	 */
	
	public static PeerMessage readMessageFromInputStream(DataInputStream dis) throws IOException {
		
		PeerMessage message = new PeerMessage();

		byte opcode = dis.readByte();								
				
		message.opcode = opcode;
		
		switch (opcode) {
		
			case PeerMessageOps.OPCODE_INVALID_CODE:
				
				break;
		
			case PeerMessageOps.OPCODE_FILE_NOT_FOUND:					
				break;
			
			case PeerMessageOps.OPCODE_GET_CHUNCK:
				

			    short hashChunkLen = dis.readShort();                		// leemos cuanto ocupa el hash
			    byte[] hashChunkBytes = new byte[hashChunkLen];    			// obtenemos exactamente el numero de bytes necesario
			    dis.readFully(hashChunkBytes);
			    
			    message.hashSubstring = new String(hashChunkBytes);   	 	// convierte los bytes a string
			    message.fileOffset = dis.readLong();                    	// leemos el desplazamiento en el fichero
			    message.chunkSize = dis.readInt();                        	// leemos el tamaño del fragmento solicitado

			    break;
			
			case PeerMessageOps.OPCODE_GET_FILE_HASH:		
				
				short hashLen = dis.readShort();							// leemos cuanto ocupa el texto
				byte[] hashBytes = new byte[hashLen];						// obtenemos exactamente el numero de bytes necesario
				dis.readFully(hashBytes);								
				
				message.hashSubstring = new String(hashBytes);				// convierte los bytes a string 
				
				break;
		
			case PeerMessageOps.OPCODE_FILE_LIST:							
				
				int numF = dis.readInt();									// lee cuantos ficheros tiene la lista

				message.fileList = new FileInfo[numF];						// inicializa el array con el tamaño exacto necesario
					
				for(int i = 0 ; i < numF ; i++) {							// para cada fichero: 
										
					short nameLen = dis.readShort();					 
					byte[] nameBuf = new byte[nameLen];
					
					dis.readFully(nameBuf);
					String fileName = new String(nameBuf, StandardCharsets.UTF_8);
					
					// lee el tamaño del fichero como un long
					
					long fileSize = dis.readLong();		
					
					// obtenemos el hash del fichero como String
					
					short fileHashLen = dis.readShort();
					byte[] fileHashBuf = new byte[fileHashLen];
					dis.readFully(fileHashBuf);
					
					String fileH = new String(fileHashBuf, StandardCharsets.UTF_8);
					
					// y por ultimo creamos un objeto FileInfo al que le pasamos la informacion obtenida
					
					FileInfo f = new FileInfo(fileH, fileName, fileSize, null);
						
					message.fileList[i] = f;								// y almacenamos ese objeto en nuestra lista(array)
				}		
				
				break;
				
			case PeerMessageOps.OPCODE_SEND_FILE: {
			    
				int hashLeng = dis.readUnsignedShort();
				
			    byte[] hashB = new byte[hashLeng];
			    
			    dis.readFully(hashB);
			    message.hashSubstring = new String(hashB, java.nio.charset.StandardCharsets.UTF_8);

			    int nameLen = dis.readUnsignedShort();
			    byte[] nameBytes = new byte[nameLen];
			    dis.readFully(nameBytes);
			    message.fileName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

			    message.fileSize = dis.readLong();
			    message.fileOffset = dis.readLong();

			    int dataLen = dis.readInt();
			    if (dataLen > 0) {
			        message.data = new byte[dataLen];
			        dis.readFully(message.data);
			    } else {
			        message.data = new byte[0];
			    }

			    break;
			}
				
			default:
				System.err.println("PeerMessage.readMessageFromInputStream doesn't know how to parse this message opcode: "
						+ PeerMessageOps.opcodeToOperation(opcode));
				System.exit(-1);
		}
		
		return message;														// devolvemos el objeto que creamos al principio del metodo
	}

	public void writeMessageToOutputStream(DataOutputStream dos) throws IOException {	// aqui el opcode llega correctamente

		dos.writeByte(this.opcode);									// escribe un unico byte que representa el tipo de mensaje
		dos.flush();
		switch (opcode) {
		
			case PeerMessageOps.OPCODE_FILE_NOT_FOUND:
				break;
				
			case PeerMessageOps.OPCODE_GET_CHUNCK:						// par solicitar una parte del fichero
				 
				byte[] hashChunkBytes = hashSubstring.getBytes();    	// enviamos tambien el hash del fichero solicitado
				    dos.writeShort(hashChunkBytes.length);
				    dos.write(hashChunkBytes);
				    dos.writeLong(fileOffset);                        	// escribe un entero de 8 bytes que dice donde empezar a leer en el fichero
				    dos.writeInt(chunkSize);                        	// escribe un entero de 4 bytes con la cantidad de bytes que se solicitan
				    
				    break;
				    
			case PeerMessageOps.OPCODE_GET_FILE_HASH:					// implementa el formato TLV -> Tipo-Longitud-Valor
				byte[] hashBytes = hashSubstring.getBytes();			// obtenemos los bytes del String para poder enviarlo binariamente
				dos.writeShort(hashBytes.length);						// mandamos la longitud del texto
				dos.write(hashBytes);									// y enviamos el contenido de la cadena
				break;
				
			case PeerMessageOps.OPCODE_FILE_LIST: {
			    FileInfo[] filesToSend = fileList;

			    if (filesToSend != null) {
			        dos.writeInt(filesToSend.length);

			        for (FileInfo f : filesToSend) {
			            byte[] nameBytes = f.fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			            dos.writeShort(nameBytes.length);
			            dos.write(nameBytes);

			            dos.writeLong(f.fileSize);

			            byte[] hashBy = f.fileHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			            dos.writeShort(hashBy.length);
			            dos.write(hashBy);
			        }
			    } else {
			        dos.writeInt(0);
			    }

			    break;
			}
				
		    case PeerMessageOps.OPCODE_SEND_FILE: {
		        
		    	byte[] hashB;
		    	
		        if (hashSubstring != null) {
		            hashB = hashSubstring.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		        } else {
		            hashB = new byte[0];
		        }
		        
		        dos.writeShort(hashB.length);
		        dos.write(hashB);

		        byte[] nameBytes;
		        
		        if (fileName != null) {
		            nameBytes = fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		        } else {
		            nameBytes = new byte[0];
		        }
		        
		        dos.writeShort(nameBytes.length);
		        dos.write(nameBytes);

		        dos.writeLong(fileSize);
		        dos.writeLong(fileOffset);

		        byte[] payload;
		        
		        if (data != null) {
		            payload = data;
		        } else {
		            payload = new byte[0];
		        }

		        dos.writeInt(payload.length);
		        dos.write(payload);

		        break;
		    }

				
				
			default:
				System.err.println("PeerMessage.writeMessageToOutputStream found unexpected message opcode " + opcode + "("
						+ PeerMessageOps.opcodeToOperation(opcode) + ")");
		}
		dos.flush();
	}
}
