package es.um.redes.nanoFiles.udp.message;

public class DirMessageOps {

	/*
	 * TODO: (Boletín MensajesASCII) Añadir aquí todas las constantes que definen
	 * los diferentes tipos de mensajes del protocolo de comunicación con el
	 * directorio (valores posibles del campo "operation").
	 * TODO: definir las operaciones del protocolo de directorio
	 * ---------------HECHO------------------
	 */
	public static final String OPERATION_INVALID = "invalid_operation";
	public static final String OPERATION_PING = "ping";
	public static final String OPERATION_REGISTER = "serve";		// AÑADIDA
	public static final String OPERATION_DIRFILES = "dirfiles";		// AÑADIDA
	public static final String OPERATION_PEERS = "peers";			// AÑADIDA
	public static final String OPERATION_ERROR = "error";			// AÑADIDA
	public static final String OPERATION_DOWNLOAD = "dirdl";		// AÑADIDA
	public static final String OPERATION_QUIT = "quit";				// AÑADIDA
	public static final String OPERATION_QUIT_OK = "quitok";		// AÑADIDA
	public static final String WELCOME = "welcome";					// AÑADIDA
	public static final String OPERATION_DOWNLOAD_OK = "downloadok";// AÑADIDA
	public static final String OPERATION_DIRFILES_OK = "dirfilesok";
	public static final String OPERATION_REGISTER_OK = "serveok";
	public static final String OPERATION_PEERS_OK = "peersok";
		

}
