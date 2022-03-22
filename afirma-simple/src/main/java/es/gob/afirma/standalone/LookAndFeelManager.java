/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.standalone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.logging.Logger;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import es.gob.afirma.core.misc.Platform;

/** Maneja el LookAndFeel aplicado al aplicativo.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s.
 * @author Carlos Gamuci. */
public final class LookAndFeelManager {

    private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private LookAndFeelManager() {
		// No permitimos la instanciacion
	}

    /** Color de fondo por defecto para los JPanel, JFrame y Applet. */
    public static final Color DEFAULT_COLOR;

    /** Color de fondo secundario para los JPanel, JFrame y Applet. */
    public static final Color SECUNDARY_COLOR;

    /** Color transparente. */
    public static final Color TRANSPARENT_COLOR = new Color(255, 255, 255, 0);

    /** Indica si el sistema operativo tiene activada una combinaci&oacute;n de colores de alto contraste. */
    public static final boolean HIGH_CONTRAST;

    /** Tama&ntilde;o m&aacute;ximo de las fuentes por defecto antes de considerarse grandes. */
    private static final int LARGE_FONT_LIMIT = 13;

    /** Indica si el sistema operativo tiene activada una combinaci&oacute;n de colores de alto contraste. */
    private static final boolean LARGE_FONT;

    private static Dimension screenSize = null;

    static {

    	// Obtenemos el color de la ventanas. Se protege porque puede producir errores en
    	// algunas distribuciones de Linux
    	Color windowColor;
    	try {
	    	 windowColor = UIManager.getColor("window") != null ? //$NON-NLS-1$
	    			 new Color(UIManager.getColor("window").getRGB()) : //$NON-NLS-1$
	    			 new Color(238, 238, 238);
    	}
    	catch (final Throwable e) {
    		windowColor = new Color(238, 238, 238);
		}
    	DEFAULT_COLOR = windowColor;

        final Object highContrast = Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on"); //$NON-NLS-1$
        if (highContrast instanceof Boolean) {
            HIGH_CONTRAST = ((Boolean) highContrast).booleanValue();
        }
        // En Linux usamos siempre la misma configuracion que al detectar la combinacion de colores
        // de alto contraste
        else if (Platform.OS.LINUX.equals(Platform.getOS())) {
            HIGH_CONTRAST = true;
        }
        else {
            HIGH_CONTRAST = false;
        }

        SECUNDARY_COLOR = HIGH_CONTRAST ? Color.BLACK : Color.WHITE;

        final  Object defaultFontHeight = Toolkit.getDefaultToolkit().getDesktopProperty("win.defaultGUI.font.height"); //$NON-NLS-1$
        if (defaultFontHeight instanceof Integer) {
           LARGE_FONT = ((Integer) defaultFontHeight).intValue() > LARGE_FONT_LIMIT;
        }
        // En Linux usamos siempre la misma configuracion que al detectar un tamano de fuente grande
        else if (Platform.OS.LINUX.equals(Platform.getOS())) {
            LARGE_FONT = true;
        }
        else {
            LARGE_FONT = false;
        }
    }

    /** Establece el decorado de la aplicaci&oacute;n. */
    public static void applyLookAndFeel() {

    	// Comprobamos si esta activado algun modo de accesibilidad. Si es asi,
    	// usaremos el LookAndFeel del sistema
        final boolean useSystemLookAndFeel = HIGH_CONTRAST || LARGE_FONT;

        if (!useSystemLookAndFeel) {
            UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE); //$NON-NLS-1$
            UIManager.put("RootPane.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("TextPane.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("TextArea.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("InternalFrameTitlePane.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("InternalFrame.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("Label.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("PopupMenuSeparator.background", DEFAULT_COLOR); //$NON-NLS-1$
        }

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        // Propiedades especificas para Mac OS X
        if (Platform.OS.MACOSX.equals(Platform.getOS())) {
            UIManager.put("OptionPane.background", DEFAULT_COLOR); //$NON-NLS-1$
            UIManager.put("Panel.background", DEFAULT_COLOR); //$NON-NLS-1$
            System.setProperty("apple.awt.brushMetalLook", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.awt.antialiasing", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.awt.textantialiasing", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.awt.rendering", "quality"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.awt.graphics.EnableQ2DX", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.awt.graphics.EnableDeferredUpdates", "true"); //$NON-NLS-1$ //$NON-NLS-2$
            System.setProperty("apple.laf.useScreenMenuBar", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        // Configuracion necesaria para que la aplicacion se muestre correctamente en pantallas HDPI
        else if (Platform.OS.WINDOWS.equals(Platform.getOS()) && HDPIManager.isHDPIDevice()) {
           	setLookAndFeel("Metal"); //$NON-NLS-1$
        }
        // Configuramos el Look&Feel del sistema si se considero necesario por los modos de accesibilidad
        else if(useSystemLookAndFeel){
        	try {
        		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        	}
        	catch (final Exception e2) {
        		LOGGER.warning(
        				"No se ha podido establecer ningun 'Look&Feel': " + e2 //$NON-NLS-1$
        				);
        	}
        }
        else {
        	setLookAndFeel("Nimbus"); //$NON-NLS-1$
        }
    }

    //Define el look and feel
    private static void setLookAndFeel(final String lookandfeelName) {
    	try {
            for (final LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (lookandfeelName.equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                	return;
             	}
             }

    	}
    	 catch (final Exception e) {
             LOGGER.warning(
                    "No se ha podido establecer el 'Look&Feel' " + lookandfeelName + ": " + e //$NON-NLS-1$ //$NON-NLS-2$
             );
         }
    }

    /**
     * Obtiene el tama&ntilde;o de pantalla.
     * @return Tama&ntilde;o de pantalla.
     */
    public static Dimension getScreenSize() {
    	if (screenSize == null) {
    		screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    	}
    	return screenSize;
    }

	/**
	 * Indica si es necesario maximizar las ventanas para asegurar una buena visualizaci&oacute;n.
	 * @return {@code true} si se debe maximizar la pantalla, {@code false} en caso contrario.
	 */
	public static boolean needMaximizeWindow() {
		return getScreenSize().height <= 600;
	}
}
