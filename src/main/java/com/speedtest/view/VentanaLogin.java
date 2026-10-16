package com.speedtest.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * Tarea #4 (HU-01 / RF-GUI-01): Vista de autenticación de operadores en Swing.
 * Incluye JTextField, JPasswordField, JButton, etiqueta de estado y soporte
 * para tecla Enter.
 */
public class VentanaLogin extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnIngresar;
    private JLabel lblEstado;

    public VentanaLogin() {
        inicializarComponentes();
    }

    private void inicializarComponentes() {
        setTitle("SpeedTest Desktop - Autenticación de Operador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setLayout(new BorderLayout());

        // 1. Panel de Encabezado Institucional
        JPanel panelEncabezado = new JPanel(new BorderLayout(0, 4));
        panelEncabezado.setBackground(new Color(24, 43, 73));
        panelEncabezado.setBorder(BorderFactory.createEmptyBorder(18, 25, 18, 25));

        JLabel lblTitulo = new JLabel("SPEEDTEST DESKTOP", SwingConstants.CENTER);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitulo.setForeground(Color.WHITE);

        JLabel lblSubtitulo = new JLabel("Supervisión de Rendimiento y Auditoría QoS - EIEE Univalle", SwingConstants.CENTER);
        lblSubtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitulo.setForeground(new Color(200, 214, 229));

        panelEncabezado.add(lblTitulo, BorderLayout.NORTH);
        panelEncabezado.add(lblSubtitulo, BorderLayout.CENTER);

        // 2. Panel Central del Formulario
        JPanel panelFormulario = new JPanel(new GridBagLayout());
        panelFormulario.setBackground(Color.WHITE);
        panelFormulario.setBorder(BorderFactory.createEmptyBorder(25, 35, 20, 35));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.weightx = 1.0;

        // Etiqueta Usuario
        JLabel lblUsuario = new JLabel("Usuario del Operador:");
        lblUsuario.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUsuario.setForeground(new Color(45, 52, 54));
        gbc.gridx = 0;
        gbc.gridy = 0;
        panelFormulario.add(lblUsuario, gbc);

        // Campo de texto Usuario
        txtUsername = new JTextField(20);
        txtUsername.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtUsername.setPreferredSize(new Dimension(280, 34));
        txtUsername.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 190, 200), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        gbc.gridy = 1;
        panelFormulario.add(txtUsername, gbc);

        // Etiqueta Contraseña
        JLabel lblPassword = new JLabel("Contraseña:");
        lblPassword.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblPassword.setForeground(new Color(45, 52, 54));
        gbc.gridy = 2;
        gbc.insets = new Insets(12, 0, 6, 0);
        panelFormulario.add(lblPassword, gbc);

        // Campo de Contraseña
        txtPassword = new JPasswordField(20);
        txtPassword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPassword.setPreferredSize(new Dimension(280, 34));
        txtPassword.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 190, 200), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        gbc.gridy = 3;
        gbc.insets = new Insets(6, 0, 6, 0);
        panelFormulario.add(txtPassword, gbc);

        // Botón de Ingreso
        btnIngresar = new JButton("Iniciar Sesión");
        btnIngresar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnIngresar.setBackground(new Color(0, 102, 204));
        btnIngresar.setForeground(Color.WHITE);
        btnIngresar.setFocusPainted(false);
        btnIngresar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnIngresar.setPreferredSize(new Dimension(280, 38));
        gbc.gridy = 4;
        gbc.insets = new Insets(18, 0, 10, 0);
        panelFormulario.add(btnIngresar, gbc);

        // Etiqueta de Estado en tiempo real
        lblEstado = new JLabel("Ingrese sus credenciales para conectar con MySQL.", SwingConstants.CENTER);
        lblEstado.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblEstado.setForeground(new Color(99, 110, 114));
        gbc.gridy = 5;
        gbc.insets = new Insets(4, 0, 0, 0);
        panelFormulario.add(lblEstado, gbc);

        // Permitir que la tecla Enter en cualquier parte o campo dispare el botón
        getRootPane().setDefaultButton(btnIngresar);

        // Ensamblar en el JFrame
        getContentPane().add(panelEncabezado, BorderLayout.NORTH);
        getContentPane().add(panelFormulario, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null); // Centrar en pantalla
    }

    // =========================================================================
    // API PÚBLICA DESACOPLADA PARA EL CONTROLADOR (MVC)
    // =========================================================================
    public String getUsername() {
        return txtUsername.getText().trim();
    }

    public String getPassword() {
        return new String(txtPassword.getPassword());
    }

    public void limpiarPassword() {
        txtPassword.setText("");
        txtPassword.requestFocusInWindow();
    }

    public void enfocarUsername() {
        txtUsername.requestFocusInWindow();
    }

    /**
     * Enlaza tanto el clic del botón como la pulsación de Enter en los campos
     * de texto.
     */
    public void addAutenticacionListener(ActionListener listener) {
        btnIngresar.addActionListener(listener);
        txtUsername.addActionListener(listener);
        txtPassword.addActionListener(listener);
    }

    /**
     * Bloquea o desbloquea los controles durante la validación asíncrona para
     * no saturar el EDT.
     */
    public void setControlesHabilitados(boolean habilitado) {
        txtUsername.setEnabled(habilitado);
        txtPassword.setEnabled(habilitado);
        btnIngresar.setEnabled(habilitado);
        setCursor(habilitado ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    /**
     * Actualiza el mensaje de retroalimentación visual en la parte inferior del
     * formulario.
     */
    public void mostrarEstado(String mensaje, Color color) {
        lblEstado.setText(mensaje);
        lblEstado.setForeground(color);
    }
}
