/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.standalone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.standalone.updater.Updater;

/** Gestor de los recursos de las diferentes formas de ayuda de la aplicaci&oacute;n,
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s */
public final class HelpResourceManager {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Longitud m&aacute;xima del texto de versi&oacute;n que se procesar&aacute;. */
	private static final int MAX_VERSION_LENGTH = 30;

	/** Nombre del directorio con los ficheros de ayuda. */
	private static final String INTERNAL_HELP_DIR_NAME = "help/"; //$NON-NLS-1$

	/** Nombre del directorio con los ficheros de ayuda. */
	private static final String INDEX_HELP_FILENAME = "help/fileslist.txt"; //$NON-NLS-1$

	private static final String HELP_VERSION_FILENAME = "help.version"; //$NON-NLS-1$

	private HelpResourceManager() {
		// No permitimos instanciar
	}

	static void extractHelpResources(final File helpDir) throws IOException {

		// Extraemos los ficheros de ayuda
		extractResources(helpDir);

		// Creamos un fichero que marca la version de la aplicacion a la que corresponden estos
		// ficheros de ayuda
		try (final FileOutputStream fos = new FileOutputStream(new File(helpDir, HELP_VERSION_FILENAME))) {
			fos.write(Updater.getCurrentVersionText().getBytes());
		}
	}


	private static void extractResources(final File helpDir) throws IOException {

		// Creamos el directorio de los recursos de ayuda si no existiese
		createHelpDir(helpDir);

		// Vamos a leer el fichero de indice linea a linea y copiando a disco los ficheros
		// listados
    	try (final InputStream indexIs = ClassLoader.getSystemResourceAsStream(INDEX_HELP_FILENAME);
    			final InputStreamReader isr = new InputStreamReader(indexIs);
    			final BufferedReader index = new BufferedReader(isr);) {

    		// Leemos la siguiente entrada
    		String filePath;
    		while ((filePath = index.readLine()) != null) {
    			if (!filePath.isBlank()) {

    				// Cargamos el recurso
    				try (final InputStream fileIs = ClassLoader.getSystemResourceAsStream(INTERNAL_HELP_DIR_NAME + filePath)) {

    					final File resourceFile = new File(helpDir, filePath.replace("/", File.separator)); //$NON-NLS-1$

    					// Si no existia el directorio en el que se debe guardar, se crea
    					if (!resourceFile.getParentFile().exists()) {
    						resourceFile.getParentFile().mkdirs();
    					}

    					// Creamos el fichero local
    					try (final FileOutputStream fos = new FileOutputStream(resourceFile)) {
    						fos.write(AOUtil.getDataFromInputStream(fileIs));
    					}
    				}
    				catch (final Exception e) {
    					LOGGER.log(Level.WARNING, "No se ha encontrado o no ha podido leerse el fichero de ayuda " + filePath, e); //$NON-NLS-1$
    				}
    			}
    		}


    	}

	}

    /** Crea el directorio de usuario del programa si no existe, */
    private static void createHelpDir(final File helpDir) {
    	if (!helpDir.exists()) {
    		helpDir.mkdirs();
    	}
    }

	/**
	 * Indica si un fichero con la versi&oacute;n de la ayuda de la aplicaci&oacute;n
	 * tiene una versi&oacute;n que no se corresponde con la actual.
	 * @param helpVersionFile Fichero con la versi&oacute;n de la ayuda.
	 * @return {@code true} si el fichero de version indicado no se corresponde con el
	 * de la versi&oacute;n actual. {@code false} en caso contrario.
	 */
	public static boolean isDifferentHelpFile(final File helpVersionFile) {
		if (!helpVersionFile.exists()) {
			return true;
		}

		final byte[] fileContent = new byte[(int) Math.min(helpVersionFile.length(), MAX_VERSION_LENGTH)];
		try (FileInputStream fis = new FileInputStream(helpVersionFile);) {
			fis.read(fileContent);
		}
		catch (final Exception e) {
			LOGGER.warning("No se pudo leer el fichero con la version de la ayuda. Se generara la nueva ayuda: " + e); //$NON-NLS-1$
			return true;
		}
		final String trimFileVersion = new String(fileContent, StandardCharsets.UTF_8);

		final String currentAppVersion = Updater.getCurrentVersion();
		final String trimAppVersion = currentAppVersion.substring(0,  Math.min(currentAppVersion.length(), MAX_VERSION_LENGTH));

		return !trimFileVersion.equals(trimAppVersion);
	}

	/**
	 * Comprueba si la ayuda almacenada en local se corresponde a la de la versi&oacute;n actual
	 * de la aplicaci&oacute;n.
	 * @param helpDir Directorio en el que debe encontrarse la ayuda.
	 * @return {@code true} si se encuentra almacenada en local la ayuda de la versi&oacute;n de la
	 * aplicaci&oacute;n que se est&aacute; ejecutando, {@code false} si no hay almacenada ayuda o
	 * si se trata de otra versi&oacute;n.
	 */
	static boolean isLocalHelpUpdated(final File helpDir) {

		boolean updated;
		try (InputStream is = new FileInputStream(new File(helpDir, HELP_VERSION_FILENAME))) {
			final String installedVersion = new String(AOUtil.getDataFromInputStream(is));
			updated = Updater.getCurrentVersionText().equals(installedVersion);
		}
		catch (final Exception e) {
			if (!(e instanceof FileNotFoundException)) {
				LOGGER.log(Level.WARNING, "No se ha podido leer el fichero de version de la ayuda local", e); //$NON-NLS-1$
			}
			updated = false;
		}

		return updated;
	}
}
