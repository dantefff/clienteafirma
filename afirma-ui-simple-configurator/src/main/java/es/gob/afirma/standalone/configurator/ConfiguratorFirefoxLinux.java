package es.gob.afirma.standalone.configurator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import es.gob.afirma.core.misc.BoundedBufferedReader;

/** Configurador para instalar un certificado SSL de confianza en Mozilla NSS.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
final class ConfiguratorFirefoxLinux {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final String FILE_AUTOFIRMA_CERTIFICATE = "AutoFirma_ROOT.cer"; //$NON-NLS-1$
	private static final String CERTUTIL_EXE = "certutil"; //$NON-NLS-1$
	private static final String CERTUTIL_RELATIVE_PATH = "certutil" + File.separator + CERTUTIL_EXE; //$NON-NLS-1$

	private static final String PROFILES_INI_RELATIVE_PATH = ".mozilla/firefox/profiles.ini";//$NON-NLS-1$
	private static final String SYSTEM_KEYSTORE_PATH = "/.pki/nssdb";//$NON-NLS-1$

	private static final String PROFILE_INI_PATH_PREFIX = "Path="; //$NON-NLS-1$

	private static final String CERTUTIL_RESOURCE = "/linux/certutil.linux.zip"; //$NON-NLS-1$

	private ConfiguratorFirefoxLinux() {
		// No instanciable
	}

	/**
	 * Genera los scripts para la instalaci&oacute;n y desinstalaci&oacute;n de los certificados
	 * en el almac&eacute;n central del sistema.
	 * De la ejecuci&oacute;n de los scripts se encarga el instalador/desinstalador Debian.
	 * @param appDir Directorio en el que se encuentra la aplicaci&oacute;n y el certificado a instalar.
	 * @param usersDirs Directorios de usuario.
	 * @param installScriptFile Fichero al que agregar el script de instalaci&oacute;n.
	 * @param uninstallScriptFile Fichero al que agregar el script de desinstalaci&oacute;n.
	 * @throws IOException Cuando ocurre un error al crear los scripts.
	 */
	static void createScriptsToSystemKeyStore(final File appDir, final String[] usersDirs,
			final File installScriptFile, final File uninstallScriptFile)
			throws IOException {

		// Comprobamos que certutil este disponible y, si no, copiamos una version propia
		String certUtilPath;
		try {
			checkCertUtil(CERTUTIL_EXE);
			certUtilPath = CERTUTIL_EXE;
		}
		catch (final Exception e) {
			ConfiguratorUtil.uncompressResource(CERTUTIL_RESOURCE, appDir);
			certUtilPath = new File(appDir, CERTUTIL_RELATIVE_PATH).getAbsolutePath();
		}

		// Creamos los scripts para cada uno de los usuarios
		for (final String userDir : usersDirs) {
			final File file = new File(userDir + SYSTEM_KEYSTORE_PATH);
			if (!file.isDirectory()) {
				continue;
			}

			final String profileReference = escapePath("sql:" + userDir + SYSTEM_KEYSTORE_PATH); //$NON-NLS-1$

			// Creamos el script de instalacion
			final String[] installCACommands = new String[] {
					certUtilPath,
					"-d", //$NON-NLS-1$
					profileReference,
					"-A", //$NON-NLS-1$
					"-n", //$NON-NLS-1$
					"\"" + ConfiguratorUtil.CERT_ALIAS + "\"", //$NON-NLS-1$ //$NON-NLS-2$
					"-i", //$NON-NLS-1$
					escapePath(new File(appDir, FILE_AUTOFIRMA_CERTIFICATE).getAbsolutePath()),
					"-t", //$NON-NLS-1$
					"\"TCP,TCP,TCP\"" //$NON-NLS-1$
			};
			createScript(installCACommands, installScriptFile);

			// Generamos el script de desinstalacion
			final String[] uninstallCACommans = new String[] {
					certUtilPath,
					"-D", //$NON-NLS-1$
					"-d", //$NON-NLS-1$
					profileReference,
					"-n", //$NON-NLS-1$
					escapePath(appDir.getAbsolutePath())
			};
			createScript(uninstallCACommans, uninstallScriptFile);
		}
	}

	/**
	 * Genera los scripts para la instalaci&oacute;n y desinstalaci&oacute;n de los certificados
	 * en el almac&eacute;n central del sistema.
	 * De la ejecuci&oacute;n de los scripts se encarga el instalador/desinstalador Debian.
	 * @param appDir Directorio en el que se encuentra la aplicaci&oacute;n y el certificado a instalar.
	 * @param usersDirs Directorios de usuario.
	 * @param installScriptFile Fichero al que agregar el script de instalaci&oacute;n.
	 * @param uninstallScriptFile Fichero al que agregar el script de desinstalaci&oacute;n.
	 * @throws IOException Cuando ocurre un error al crear los scripts.
	 * @throws MozillaProfileNotFoundException No se han encontrado directorios de perfil de Mozilla.
	 */
	static void createScriptsToMozillaKeyStore(final File appDir, final String[] usersDirs,
			final File installScriptFile, final File uninstallScriptFile)
			throws MozillaProfileNotFoundException, IOException {

		// Comprobamos que certutil este disponible y, si no, copiamos una version propia
		String certUtilPath;
		try {
			checkCertUtil(CERTUTIL_EXE);
			certUtilPath = CERTUTIL_EXE;
		}
		catch (final Exception e) {
			try {
				ConfiguratorUtil.uncompressResource(CERTUTIL_RESOURCE, appDir);
			}
			catch (final Exception ex) {
				LOGGER.warning("No se ha podido descomprimir CertUtil. Se omite la configuracion del certificado SSL en Mozilla Firefox: " + ex); //$NON-NLS-1$
				return;
			}
			certUtilPath = new File(appDir, CERTUTIL_RELATIVE_PATH).getAbsolutePath();
		}

		final List<File> profileDirs = getProfiles(usersDirs);
		if (profileDirs == null || profileDirs.isEmpty()){
			LOGGER.info("No se encuentran fichero de perfil de Mozilla, por lo que no se instalaran certificados"); //$NON-NLS-1$
			throw new MozillaProfileNotFoundException();
		}

		writeScriptsToMozillaKeyStoreWithCheck(certUtilPath, appDir, profileDirs, installScriptFile, uninstallScriptFile);

	}

	//TODO: Comprobar que es necesario el mecanismo de reintento y deteccion de cancelacion
	/**
	 * Escribe en disco los scripts para la instalaci&oacute;n/desinstalaci&oacute;n del
	 * certificado de CA del certificado SSL.
	 * @param certUtilAbsolutePath Ruta del ejecutable certutil.
	 * @param appDir Directorio de la aplicaci&oacute;n.
	 * @param profileDirs Listado de directorios de perfiles de Mozilla.
	 * @param installScriptFile Fichero al que agregar el script de instalaci&oacute;n.
	 * @param uninstallScriptFile Fichero al que agregar el script de desinstalaci&oacute;n.
	 */
	private static void writeScriptsToMozillaKeyStoreWithCheck (
													   final String certUtilAbsolutePath,
													   final File appDir,
			                                           final List<File> profileDirs,
			                               			   final File installScriptFile,
			                               			   final File uninstallScriptFile) {
		boolean installed = false;
		boolean cancelled = false;
		do {
			try {
				writeScriptsToMozillaKeyStore(
						certUtilAbsolutePath,
						appDir,
						profileDirs,
						installScriptFile,
						uninstallScriptFile);
				installed = true;
			}
			catch (final Exception e) {
				LOGGER.log(Level.WARNING,
						"No se pudo crear el script de instalacion del certificado SSL para el socket en el almacen de Firefox: " + e, //$NON-NLS-1$
						e);
				final int result = JOptionPane.showConfirmDialog(
						null,
						Messages.getString("ConfiguratorWindows.10"), //$NON-NLS-1$
						Messages.getString("ConfiguratorWindows.1"), //$NON-NLS-1$
						JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.WARNING_MESSAGE
						);
				if (result == JOptionPane.CANCEL_OPTION) {
					cancelled = true;
					LOGGER.warning(
							"El usuario cancelo la instalacion del certificado SSL para el socket en Firefox: " + e //$NON-NLS-1$
							);
				}
			}
		} while (!installed && !cancelled);

	}

	/** Ejecuta la utilidad Mozilla CertUtil para la instalaci&oacute;n del certificado ra&iacute;z de  confianza en Firefox.
	 * @param certUtilAbsolutePath Ruta del ejecutable certutil.
	 * @param certDir Directorio en el que se encuentra el certificado a importar.
	 * @param profilesDir Listado de directorios de perfiles de usuario de Mozilla Firefox.
	 * @param installScriptFile Fichero al que agregar el script de instalaci&oacute;n.
	 * @param uninstallScriptFile Fichero al que agregar el script de desinstalaci&oacute;n.
	 * @throws IOException Cuando ocurre un error en el tratamiento de datos.
	 * @throws GeneralSecurityException Cuando ocurre un error en la inserci&oacute;n del certificado en el KeyStore.
	 */
	private static void writeScriptsToMozillaKeyStore(
												final String certUtilAbsolutePath,
			                                    final File certDir,
			                                    final List<File> profilesDir,
		                               			final File installScriptFile,
		                               			final File uninstallScriptFile) throws IOException,
	                                                                             GeneralSecurityException {

		for (final File profileDir : profilesDir) {
			if (!profileDir.isDirectory()) {
				continue;
			}

			// Si en el directorio del perfil existe el fichero pkcs11.txt entonces se trata
			// de un almacen de certificados SQL
			final boolean sqlDb = new File(profileDir, "pkcs11.txt").exists(); //$NON-NLS-1$
			final String profileReference = escapePath((sqlDb ? "sql:" : "") + profileDir.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$

			// Creamos el script de instalacion
			final String[] installCACommands = new String[] {
					certUtilAbsolutePath,
					"-A", //$NON-NLS-1$
					"-d", //$NON-NLS-1$
					profileReference,
					"-i", //$NON-NLS-1$
					escapePath(new File(certDir, FILE_AUTOFIRMA_CERTIFICATE).getAbsolutePath()),
					"-n", //$NON-NLS-1$
					"\"" + ConfiguratorUtil.CERT_ALIAS + "\"", //$NON-NLS-1$ //$NON-NLS-2$
					"-t", //$NON-NLS-1$
					"\"C,,\"" //$NON-NLS-1$
			};
			createScript(installCACommands, installScriptFile);

			// Generamos el script de desinstalacion
			final String[] uninstallCACommans = new String[] {
					certUtilAbsolutePath,
					"-D", //$NON-NLS-1$
					"-d", //$NON-NLS-1$
					profileReference,
					"-n", //$NON-NLS-1$
					escapePath(certDir.getAbsolutePath())
			};
			createScript(uninstallCACommans, uninstallScriptFile);
		}
	}

	/** Escribe en disco los scripts de instalaci&oacute;n y desinstalaci&oacute;n para un almac&eacute;n
	 * de claves a partir del comando de instalaci&oacute;n.
	 * @param commands Comando para la instalaci&oacute;n del certificado en un almac&eacute;n.
	 * @param profileReference Referencia al perfil de Mozilla.
	 * @param certDir Directorio en el que se encuentra el certificado.
	 * @throws IOException Si no se pudo realizar la propia ejecuci&oacute;n. */
	private static void createScript(final String[] commands, final File scriptFile)
			throws IOException {

		// Generamos el script de instalacion
		final StringBuilder script = new StringBuilder();
		ConfiguratorUtil.printScript(commands, script);
		ConfiguratorUtil.writeScript(script, scriptFile);
	}

	/** Obtiene todos los perfiles de Mozilla para los usuarios indicado
	 * @param userDirs Directorios de usuario.
	 * @return Listado de directorios de perfil de Mozilla. */
	private static List<File> getProfiles(final String[] userDirs){

		final List <File> mozillaProfilesIniPaths = getMozillaProfilesIniPaths(userDirs);
		if (mozillaProfilesIniPaths == null || mozillaProfilesIniPaths.isEmpty()) {
			return null;
		}

		final List<File> profileDirs = new ArrayList<>();
		for (final File profilesIniFile : mozillaProfilesIniPaths){
			String line;
			try (
				final InputStream resIs = new FileInputStream(profilesIniFile);
				final BufferedReader resReader = new BoundedBufferedReader(
					new InputStreamReader(resIs),
					256, // Maximo 256 lineas de salida (256 perfiles por "profiles.ini")
					2048 // Maximo 2048 caracteres por linea
				);
			) {
				while ((line = resReader.readLine()) != null) {
					if (line.startsWith(PROFILE_INI_PATH_PREFIX)){
						final File profileDir = new File(
							profilesIniFile.getAbsolutePath().substring(
								0, profilesIniFile.getAbsolutePath().lastIndexOf("/") + 1) + line.substring(PROFILE_INI_PATH_PREFIX.length() //$NON-NLS-1$
							)
						);
						if (profileDir.isDirectory()){
							profileDirs.add(profileDir);
						}
					}
				}
			}
			catch (final Exception e) {
				LOGGER.warning("Error al buscar los directorios de perfiles de Firefox: " + e); //$NON-NLS-1$
			}
		}
		return profileDirs;
	}

	/** Devuelve un listado con los ficheros <i>profiles.ini</i> de Mozilla.
	 * @param userDirs Listado de rutas de los directorios de usuarios del sistema.
	 * @return Listado de ficheros <i>profiles.ini</i>. */
	private static List<File> getMozillaProfilesIniPaths(final String[] userDirs){

		final List<File> profilesIniFiles = new ArrayList<>();
		for (final String userDir : userDirs){
			final File mozillaPath = new File(userDir, PROFILES_INI_RELATIVE_PATH);
			if (mozillaPath.isFile()){
				profilesIniFiles.add(mozillaPath);
			}
		}
		return profilesIniFiles;
	}

	/** Comprueba si certutil existe y si puede ejecutarse.
	 * @throws IOException Cuando hay un problema con el ejecutable CertUtil. */
	private static void checkCertUtil(final String certutil) throws IOException {

		new ProcessBuilder(certutil).start();
	}

	/**
	 * Escapa una ruta de fichero para poder utilizarla como parte de un
	 * comando de consola.
	 * @param path Ruta a escapar.
	 * @return Ruta escapada.
	 */
	private static String escapePath(final String path) {
		if (path == null) {
			throw new IllegalArgumentException(
				"La ruta a 'escapar' no puede ser nula" //$NON-NLS-1$
			);
		}
		return path.replace(" ", "\\ "); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
