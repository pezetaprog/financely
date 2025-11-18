import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public class AppScraper extends JFrame {

    private final JTextField tickerField;
    private final JButton getDataButton;
    private final JButton showChartButton;
    private final JTextArea resultArea;
    private final JComboBox<String> rangeCombo;   // 👈 NUEVO

    private static final String API_KEY = "TU_API_KEY_AQUI";
    private static final String BASE_URL = "https://www.alphavantage.co/query";

    private final OkHttpClient httpClient = new OkHttpClient();

    public AppScraper() {
        setTitle("Mi Portafolio - Alpha Vantage + Gráficos");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel superior
        JPanel topPanel = new JPanel(new BorderLayout());
        tickerField = new JTextField("AAPL");
        getDataButton = new JButton("Obtener datos");
        showChartButton = new JButton("Mostrar gráfico");

        // 👇 NUEVO: combo de rango
        rangeCombo = new JComboBox<>(new String[]{
                "Últimos 30 días",
                "Últimos 3 meses",
                "Últimos 6 meses"
        });
        rangeCombo.setSelectedIndex(1); // por defecto: 3 meses

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(getDataButton);
        buttonPanel.add(showChartButton);
        buttonPanel.add(rangeCombo); // 👈 agregamos el combo al panel de botones

        topPanel.add(new JLabel("Símbolo: "), BorderLayout.WEST);
        topPanel.add(tickerField, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // Área de resultados
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        // Listeners
        getDataButton.addActionListener(this::fetchData);
        showChartButton.addActionListener(this::showChart);
    }

    private void fetchData(ActionEvent e) {
        String symbol = tickerField.getText().trim().toUpperCase();
        if (symbol.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese un símbolo (ejemplo: AAPL)", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        resultArea.setText("🔄 Consultando Alpha Vantage...\n");
        getDataButton.setEnabled(false);

        new Thread(() -> {
            try {
                String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + API_KEY;
                Request request = new Request.Builder().url(url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    String json = response.body().string();
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    JsonObject quote = obj.getAsJsonObject("Global Quote");

                    if (quote == null || quote.size() == 0) {
                        SwingUtilities.invokeLater(() -> resultArea.setText("❌ No se encontró información."));
                    } else {
                        String price = quote.get("05. price").getAsString();
                        String percent = quote.get("10. change percent").getAsString();
                        SwingUtilities.invokeLater(() -> resultArea.setText(symbol + " → 💵 " + price + " USD (" + percent + ")"));
                    }
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> resultArea.setText("❌ Error: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> getDataButton.setEnabled(true));
            }
        }).start();
    }

    private void showChart(ActionEvent e) {
        String symbol = tickerField.getText().trim().toUpperCase();
        if (symbol.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese un símbolo para graficar", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 👇 Leer rango seleccionado y convertirlo a una fecha mínima
        String selectedRange = (String) rangeCombo.getSelectedItem();
        LocalDate fromDate = calculateFromDate(selectedRange);

        showChartButton.setEnabled(false);

        new Thread(() -> {
            try {
                TimeSeries series = fetchHistoricalPrices(symbol, fromDate); // 👈 ahora pasa fromDate
                TimeSeriesCollection dataset = new TimeSeriesCollection(series);
                JFreeChart chart = ChartFactory.createTimeSeriesChart(
                        "Histórico de " + symbol,
                        "Fecha",
                        "Precio (USD)",
                        dataset,
                        true,
                        true,
                        false
                );

                SwingUtilities.invokeLater(() -> {
                    JFrame chartFrame = new JFrame("📈 Gráfico - " + symbol);
                    chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                    chartFrame.setSize(800, 600);

                    ChartPanel chartPanel = new ChartPanel(chart);
                    chartPanel.setMouseWheelEnabled(true); // pequeño plus: zoom con la rueda

                    chartFrame.add(chartPanel);
                    chartFrame.setLocationRelativeTo(null);
                    chartFrame.setVisible(true);
                    showChartButton.setEnabled(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "❌ Error al cargar gráfico: " + ex.getMessage());
                    showChartButton.setEnabled(true);
                });
            }
        }).start();
    }

    // 👇 NUEVO: calcula la fecha mínima según el rango elegido
    private LocalDate calculateFromDate(String range) {
        LocalDate today = LocalDate.now();
        if (range == null) {
            return today.minusMonths(6);
        }
        switch (range) {
            case "Últimos 30 días":
                return today.minusDays(30);
            case "Últimos 3 meses":
                return today.minusMonths(3);
            case "Últimos 6 meses":
            default:
                return today.minusMonths(6);
        }
    }

    // 👇 Modificado: ahora recibe fromDate y filtra por rango
    private TimeSeries fetchHistoricalPrices(String symbol, LocalDate fromDate) throws Exception {
        String url = BASE_URL + "?function=TIME_SERIES_DAILY&symbol=" + symbol + "&outputsize=compact&apikey=" + API_KEY;

        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject timeSeries = obj.getAsJsonObject("Time Series (Daily)");

            if (timeSeries == null) {
                throw new RuntimeException("No se encontró información histórica para " + symbol);
            }

            // TreeMap para ordenar por fecha ascendente
            TreeMap<LocalDate, Double> dataMap = new TreeMap<>();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map.Entry<String, JsonElement> entry : timeSeries.entrySet()) {
                LocalDate date = LocalDate.parse(entry.getKey(), formatter);
                double close = entry.getValue().getAsJsonObject().get("4. close").getAsDouble();
                dataMap.put(date, close);
            }

            TimeSeries series = new TimeSeries(symbol);

            // Filtramos por rango: solo fechas >= fromDate
            for (Map.Entry<LocalDate, Double> entry : dataMap.entrySet()) {
                LocalDate date = entry.getKey();
                if (date.isBefore(fromDate)) {
                    continue;
                }
                double price = entry.getValue();
                series.add(new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()), price);
            }

            return series;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppScraper().setVisible(true));
    }
}
