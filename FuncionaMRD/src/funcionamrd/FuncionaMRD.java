
package funcionamrd;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * ParkingReservationApp
 * Un solo archivo con clases internas para facilitar copia/pegado.
 *
 * Requisitos:
 * - Colocar usuarios.csv en data/usuarios.csv con formato csv: correo,nombre,categoria
 * - El programa guarda reservas en data/reservas.csv
 *
 * Java 8+
 */
public class FuncionaMRD {

    // Rutas de archivos
    private static final String USUARIOS_CSV = "data/usuarios.csv";
    private static final String RESERVAS_CSV = "data/reservas.csv";
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Controladores
    private ControladorUsuarios controladorUsuarios;
    private ControladorReservas controladorReservas;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new FuncionaMRD().start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error arrancando la aplicación: " + e.getMessage());
            }
        });
    }

    public FuncionaMRD() {
        controladorUsuarios = new ControladorUsuarios(Paths.get(USUARIOS_CSV));
        controladorReservas = new ControladorReservas(Paths.get(RESERVAS_CSV));
    }

    private void start() {
        // Asegurar carpeta data
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        LoginFrame login = new LoginFrame();
        login.setVisible(true);
    }

    /* -------------------------
           MODELOS / CONTROLADORES
       ------------------------- */

    class Usuario {
        String correo;
        String nombre;
        String categoria; // alumno, admin, docente

        Usuario(String correo, String nombre, String categoria) {
            this.correo = correo;
            this.nombre = nombre;
            this.categoria = categoria.toLowerCase();
        }
    }

    class Reserva {
        String correo;
        String nombre;
        String categoria;
        int sotano; // 1..3
        String codigoEspacio; // e.g., 01A
        LocalDate fecha;
        LocalTime inicio;
        LocalTime fin;

        Reserva(String correo, String nombre, String categoria, int sotano, String codigoEspacio,
                LocalDate fecha, LocalTime inicio, LocalTime fin) {
            this.correo = correo;
            this.nombre = nombre;
            this.categoria = categoria;
            this.sotano = sotano;
            this.codigoEspacio = codigoEspacio;
            this.fecha = fecha;
            this.inicio = inicio;
            this.fin = fin;
        }
    }

    class ControladorUsuarios {
        private Map<String, Usuario> usuarios = new HashMap<>();
        private Path csvPath;

        ControladorUsuarios(Path csvPath) {
            this.csvPath = csvPath;
            load();
        }

        private void load() {
            usuarios.clear();
            if (!Files.exists(csvPath)) return;
            try (BufferedReader br = Files.newBufferedReader(csvPath)) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    // correo,nombre,categoria
                    String[] parts = line.split(",", 3);
                    if (parts.length < 3) continue;
                    Usuario u = new Usuario(parts[0].trim(), parts[1].trim(), parts[2].trim());
                    usuarios.put(u.correo.toLowerCase(), u);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Usuario validarCorreo(String correo) {
            if (correo == null) return null;
            return usuarios.get(correo.trim().toLowerCase());
        }
    }

    class ControladorReservas {
        private Path csvPath;

        ControladorReservas(Path csvPath) {
            this.csvPath = csvPath;
            ensureFile();
        }

        private void ensureFile() {
            try {
                if (!Files.exists(csvPath)) {
                    Files.createFile(csvPath);
                    try (BufferedWriter bw = Files.newBufferedWriter(csvPath, StandardOpenOption.APPEND)) {
                        bw.write("correo,nombre,categoria,sotano,codigoEspacio,fecha,inicio,fin");
                        bw.newLine();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public synchronized void guardarReserva(Reserva r) throws IOException {
            String row = String.join(",",
                    r.correo,
                    r.nombre,
                    r.categoria,
                    String.valueOf(r.sotano),
                    r.codigoEspacio,
                    r.fecha.format(FECHA_FMT),
                    r.inicio.format(HORA_FMT),
                    r.fin.format(HORA_FMT)
            );
            try (BufferedWriter bw = Files.newBufferedWriter(csvPath, StandardOpenOption.APPEND)) {
                bw.write(row);
                bw.newLine();
            }
        }

        public List<Reserva> listarReservas() {
            List<Reserva> lista = new ArrayList<>();
            if (!Files.exists(csvPath)) return lista;
            try (BufferedReader br = Files.newBufferedReader(csvPath)) {
                String line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] p = line.split(",", 8);
                    if (p.length < 8) continue;
                    String correo = p[0];
                    String nombre = p[1];
                    String categoria = p[2];
                    int sotano = Integer.parseInt(p[3]);
                    String codigo = p[4];
                    LocalDate fecha = LocalDate.parse(p[5], FECHA_FMT);
                    LocalTime inicio = LocalTime.parse(p[6], HORA_FMT);
                    LocalTime fin = LocalTime.parse(p[7], HORA_FMT);
                    lista.add(new Reserva(correo, nombre, categoria, sotano, codigo, fecha, inicio, fin));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return lista;
        }

        // consulta si hay conflicto con una reserva existente (mismo sotano, mismo espacio, misma fecha y horas se solapan)
        public boolean hayConflicto(Reserva nueva) {
            List<Reserva> existentes = listarReservas();
            for (Reserva r : existentes) {
                if (r.sotano == nueva.sotano && r.codigoEspacio.equals(nueva.codigoEspacio) && r.fecha.equals(nueva.fecha)) {
                    // comprobar solapamiento
                    if (!(nueva.fin.isBefore(r.inicio) || nueva.inicio.isAfter(r.fin) || nueva.fin.equals(r.inicio) || nueva.inicio.equals(r.fin))) {
                        // overlapping
                        return true;
                    }
                    // Consider inclusive overlap; safer to check ranges intersection:
                    if (nueva.inicio.isBefore(r.fin) && nueva.fin.isAfter(r.inicio)) return true;
                }
            }
            return false;
        }
    }

    /* -------------------------
           VENTANAS / UI
       ------------------------- */

    class LoginFrame extends JFrame {
        private JTextField correoField;

        LoginFrame() {
            setTitle("Ingreso - Reserva Estacionamiento UTP");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(450, 200);
            setLocationRelativeTo(null);
            init();
        }

        private void init() {
            JPanel p = new JPanel(new BorderLayout(10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JLabel lbl = new JLabel("<html><h2>Ingrese su correo institucional (@utp.edu.pe)</h2></html>");
            p.add(lbl, BorderLayout.NORTH);

            correoField = new JTextField();
            p.add(correoField, BorderLayout.CENTER);

            JButton ingresar = new JButton("Ingresar");
            ingresar.addActionListener(e -> intentarIngreso());
            p.add(ingresar, BorderLayout.SOUTH);

            getContentPane().add(p);
        }

        private void intentarIngreso() {
            String correo = correoField.getText().trim();
            if (correo.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Ingrese un correo.");
                return;
            }
            if (!correo.toLowerCase().endsWith("@utp.edu.pe")) {
                JOptionPane.showMessageDialog(this, "El correo debe ser institucional @utp.edu.pe");
                return;
            }
            Usuario u = controladorUsuarios.validarCorreo(correo);
            if (u == null) {
                JOptionPane.showMessageDialog(this, "Correo no registrado en la base.");
                return;
            }
            // Abrir menu principal
            MainMenuFrame menu = new MainMenuFrame(u);
            menu.setVisible(true);
            this.dispose();
        }
    }

    class MainMenuFrame extends JFrame {
        private Usuario usuario;

        MainMenuFrame(Usuario u) {
            this.usuario = u;
            setTitle("Sistema de Reservas - Bienvenido");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600, 300);
            setLocationRelativeTo(null);
            init();
        }

        private void init() {
            JPanel panel = new JPanel(new BorderLayout(10,10));
            panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JLabel welcome = new JLabel("<html><h2>Bienvenido, " + usuario.nombre + "</h2>"
                    + "<p>Categoría: <b>" + usuario.categoria + "</b></p></html>");
            panel.add(welcome, BorderLayout.NORTH);

            JPanel botones = new JPanel(new FlowLayout());
            JButton reservarBtn = new JButton("Realizar una reserva");
            reservarBtn.addActionListener(e -> {
                SotanoSelectionFrame sFrame = new SotanoSelectionFrame(usuario);
                sFrame.setVisible(true);
                this.dispose();
            });
            botones.add(reservarBtn);

            if (usuario.categoria.equals("admin") || usuario.categoria.equals("docente")) {
                JButton verReservas = new JButton("Ver reservas realizadas");
                verReservas.addActionListener(e -> {
                    AdminViewFrame a = new AdminViewFrame();
                    a.setVisible(true);
                    this.dispose();
                });
                botones.add(verReservas);
            }
            JButton logout = new JButton("Cerrar sesión");
            logout.addActionListener(ev -> {
                LoginFrame login = new LoginFrame();
                login.setVisible(true);
                this.dispose();
            });
            botones.add(logout);

            panel.add(botones, BorderLayout.CENTER);
            getContentPane().add(panel);
        }
    }

    class SotanoSelectionFrame extends JFrame {
        private Usuario usuario;
        SotanoSelectionFrame(Usuario u) {
            this.usuario = u;
            setTitle("Seleccionar Sótano");
            setSize(400,200);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            init();
        }

        private void init() {
            JPanel p = new JPanel(new GridLayout(2, 1, 10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JLabel l = new JLabel("<html><h3>Seleccione el sótano</h3></html>", SwingConstants.CENTER);
            p.add(l);

            JPanel btns = new JPanel(new FlowLayout());
            for (int i=1;i<=3;i++) {
                int sotano = i;
                JButton b = new JButton("Sótano " + i);
                b.addActionListener(e -> {
                    MapFrame map = new MapFrame(usuario, sotano);
                    map.setVisible(true);
                    this.dispose();
                });
                btns.add(b);
            }
            p.add(btns);
            getContentPane().add(p);
        }
    }

    class MapFrame extends JFrame {
        private Usuario usuario;
        private int sotano;
        private JPanel gridPanel;
        // Definimos tamaño del mapa (filas x cols) por sótano (ejemplo simple)
        private final int ROWS = 4;
        private final int COLS = 6;

        MapFrame(Usuario user, int sotano) {
            this.usuario = user;
            this.sotano = sotano;
            setTitle("Mapa - Sótano " + sotano);
            setSize(800, 500);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            init();
        }

        private void init() {
            JPanel main = new JPanel(new BorderLayout(10,10));
            main.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JLabel top = new JLabel("<html><h3>Sótano " + sotano + " - Seleccione su espacio</h3></html>");
            main.add(top, BorderLayout.NORTH);

            gridPanel = new JPanel(new GridLayout(ROWS, COLS, 5,5));
            // Generar códigos (dos dígitos + letra) — por fila/col para variedad
            char letter = 'A';
            for (int r=0; r<ROWS; r++) {
                for (int c=0; c<COLS; c++) {
                    int num = r*COLS + c + 1;
                    String code = String.format("%02d%c", num, (char)('A' + (c % 6)));
                    JButton spaceBtn = new JButton(code);
                    spaceBtn.setPreferredSize(new Dimension(100,60));
                    spaceBtn.addActionListener(e -> {
                        // Al seleccionar espacio, abrir selector horario
                        TimeSelectionDialog dialog = new TimeSelectionDialog(this, usuario, sotano, code);
                        dialog.setVisible(true);
                    });
                    gridPanel.add(spaceBtn);
                }
            }
            JScrollPane sp = new JScrollPane(gridPanel);
            main.add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton back = new JButton("Volver");
            back.addActionListener(e -> {
                MainMenuFrame mm = new MainMenuFrame(usuario);
                mm.setVisible(true);
                this.dispose();
            });
            bottom.add(back);
            main.add(bottom, BorderLayout.SOUTH);

            getContentPane().add(main);
        }
    }

    class TimeSelectionDialog extends JDialog {
        private Usuario usuario;
        private int sotano;
        private String codigo;
        private JComboBox<String> startHourCombo;
        private JComboBox<String> startMinCombo;
        private JComboBox<String> endHourCombo;
        private JComboBox<String> endMinCombo;
        private JButton confirmarBtn;

        TimeSelectionDialog(Frame owner, Usuario usuario, int sotano, String codigo) {
            super(owner, "Seleccionar horario - " + codigo, true);
            this.usuario = usuario;
            this.sotano = sotano;
            this.codigo = codigo;
            setSize(420, 280);
            setLocationRelativeTo(owner);
            init();
        }

        private void init() {
            JPanel p = new JPanel(new BorderLayout(8,8));
            p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            p.add(new JLabel("<html><h3>Seleccionar horario para " + codigo + "</h3></html>"), BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(3,1,5,5));
            center.add(new JLabel("Fecha: (se toma la fecha de hoy) " + LocalDate.now().format(FECHA_FMT)));
            // Check domingo
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY) {
                center.add(new JLabel("<html><b style='color:red'>Hoy es Domingo: no se permiten reservas.</b></html>"));
                confirmarBtn = new JButton("Cerrar");
                confirmarBtn.addActionListener(e -> dispose());
                p.add(center, BorderLayout.CENTER);
                JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                bot.add(confirmarBtn);
                p.add(bot, BorderLayout.SOUTH);
                getContentPane().add(p);
                return;
            }

            JPanel timePanel = new JPanel(new GridLayout(2,1));
            JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            startPanel.add(new JLabel("Inicio:"));
            startHourCombo = new JComboBox<>(horasArray());
            startMinCombo = new JComboBox<>(minsArray());
            startPanel.add(startHourCombo);
            startPanel.add(new JLabel(":"));
            startPanel.add(startMinCombo);
            timePanel.add(startPanel);

            JPanel endPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            endPanel.add(new JLabel("Fin:"));
            endHourCombo = new JComboBox<>(horasArray());
            endMinCombo = new JComboBox<>(minsArray());
            endPanel.add(endHourCombo);
            endPanel.add(new JLabel(":"));
            endPanel.add(endMinCombo);
            timePanel.add(endPanel);

            center.add(timePanel);
            center.add(new JLabel("<html><i>Reglas: Horario entre 08:00 y 23:00. Inicio no puede ser dentro de 30 min. No reservar horas ya pasadas.</i></html>"));
            p.add(center, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancelar = new JButton("Cancelar");
            cancelar.addActionListener(e -> dispose());
            confirmarBtn = new JButton("Confirmar reserva");
            confirmarBtn.addActionListener(e -> confirmar());
            buttons.add(cancelar);
            buttons.add(confirmarBtn);
            p.add(buttons, BorderLayout.SOUTH);

            // Inicializar combos con valores por defecto (próxima media hora múltiplo)
            LocalTime now = LocalTime.now();
            // Round up to next 15 minutes maybe; but rule is 30 min. We'll allow times in 15min steps but enforce 30min min start distance.
            LocalTime defaultStart = now.plusMinutes(30).withSecond(0).withNano(0);
            if (defaultStart.isBefore(LocalTime.of(8,0))) defaultStart = LocalTime.of(8,0);
            if (defaultStart.isAfter(LocalTime.of(22,30))) defaultStart = LocalTime.of(22,30);

            startHourCombo.setSelectedItem(String.format("%02d", defaultStart.getHour()));
            startMinCombo.setSelectedItem(String.format("%02d", defaultStart.getMinute()));
            LocalTime defaultEnd = defaultStart.plusHours(1);
            if (defaultEnd.isAfter(LocalTime.of(23,0))) defaultEnd = LocalTime.of(23,0);
            endHourCombo.setSelectedItem(String.format("%02d", defaultEnd.getHour()));
            endMinCombo.setSelectedItem(String.format("%02d", defaultEnd.getMinute()));

            getContentPane().add(p);
        }

        private String[] horasArray() {
            String[] arr = new String[16]; // 8..23
            int idx = 0;
            for (int h=8; h<=23; h++) {
                arr[idx++] = String.format("%02d", h);
            }
            return arr;
        }

        private String[] minsArray() {
            // pasos de 15 min (00, 15, 30, 45) para facilidad
            return new String[] {"00","15","30","45"};
        }

        private void confirmar() {
            try {
                LocalDate hoy = LocalDate.now();
                if (hoy.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    JOptionPane.showMessageDialog(this, "No se permiten reservas los domingos.");
                    return;
                }
                int sh = Integer.parseInt((String) startHourCombo.getSelectedItem());
                int sm = Integer.parseInt((String) startMinCombo.getSelectedItem());
                int eh = Integer.parseInt((String) endHourCombo.getSelectedItem());
                int em = Integer.parseInt((String) endMinCombo.getSelectedItem());

                LocalTime inicio = LocalTime.of(sh, sm);
                LocalTime fin = LocalTime.of(eh, em);

                // Reglas básicas
                LocalTime minInicio = LocalTime.of(8,0);
                LocalTime maxFin = LocalTime.of(23,0);

                if (inicio.isBefore(minInicio) || fin.isAfter(maxFin) || !fin.isAfter(inicio)) {
                    JOptionPane.showMessageDialog(this, "Horario inválido. Debe estar entre 08:00 y 23:00 y fin > inicio.");
                    return;
                }

                // No reservar horas ya pasadas del día y no permitir inicios dentro de 30 minutos de ahora
                LocalDate hoyDate = LocalDate.now();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime inicioDateTime = LocalDateTime.of(hoyDate, inicio);
                if (inicioDateTime.isBefore(now)) {
                    JOptionPane.showMessageDialog(this, "No puede reservar un inicio en horario ya pasado.");
                    return;
                }
                if (Duration.between(now, inicioDateTime).toMinutes() < 30) {
                    JOptionPane.showMessageDialog(this, "El inicio debe comenzar al menos dentro de 30 minutos desde ahora.");
                    return;
                }

                // Chequear conflicto con reservas existentes
                Reserva nueva = new Reserva(usuario.correo, usuario.nombre, usuario.categoria, sotano, codigo, hoyDate, inicio, fin);
                if (controladorReservas.hayConflicto(nueva)) {
                    JOptionPane.showMessageDialog(this, "El espacio ya está reservado en ese horario.");
                    return;
                }

                // Mostrar resumen y confirmación
                String resumen = String.format("Resumen de reserva:\n\nNombre: %s\nCategoría: %s\nSótano: %d\nEspacio: %s\nFecha: %s\nHorario: %s - %s\n\nConfirmar?",
                        usuario.nombre,
                        usuario.categoria,
                        sotano,
                        codigo,
                        hoyDate.format(FECHA_FMT),
                        inicio.format(HORA_FMT),
                        fin.format(HORA_FMT));
                int opt = JOptionPane.showConfirmDialog(this, resumen, "Confirmar reserva", JOptionPane.YES_NO_OPTION);
                if (opt == JOptionPane.YES_OPTION) {
                    // Guardar
                    try {
                        controladorReservas.guardarReserva(nueva);
                        JOptionPane.showMessageDialog(this, "Reserva guardada correctamente.");
                        // Volver al menú principal del usuario
                        MainMenuFrame mm = new MainMenuFrame(usuario);
                        mm.setVisible(true);
                        this.dispose();
                        // Cerrar el owner (map) también para regresar al menu
                        Window owner = this.getOwner();
                        if (owner != null) owner.dispose();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error guardando la reserva: " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error al validar horario: " + ex.getMessage());
            }
        }
    }

    class AdminViewFrame extends JFrame {
        AdminViewFrame() {
            setTitle("Ver reservas realizadas");
            setSize(900, 500);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            init();
        }

        private void init() {
            JPanel p = new JPanel(new BorderLayout(8,8));
            p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
            JLabel title = new JLabel("<html><h3>Reservas registradas</h3></html>");
            p.add(title, BorderLayout.NORTH);

            List<Reserva> lista = controladorReservas.listarReservas();
            String[] cols = {"Correo","Nombre","Categoría","Sótano","Espacio","Fecha","Inicio","Fin"};
            DefaultTableModel model = new DefaultTableModel(cols, 0);
            for (Reserva r : lista) {
                model.addRow(new Object[] {
                        r.correo, r.nombre, r.categoria, r.sotano, r.codigoEspacio, r.fecha.format(FECHA_FMT), r.inicio.format(HORA_FMT), r.fin.format(HORA_FMT)
                });
            }
            JTable table = new JTable(model);
            JScrollPane sp = new JScrollPane(table);
            p.add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton back = new JButton("Volver");
            back.addActionListener(e -> {
                // Volver a ventana de login por simplicidad (podría volver al menu)
                LoginFrame li = new LoginFrame();
                li.setVisible(true);
                this.dispose();
            });
            bottom.add(back);
            p.add(bottom, BorderLayout.SOUTH);

            getContentPane().add(p);
        }
    }

} 