/*
    Controller.java
    The main view controller for our calculator;
 */
package teamturingbennett;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.control.TableColumn.CellEditEvent;
import java.net.URL;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class Controller implements Initializable {

    // instance fields/variables
    private final SimpleStringProperty output;
    private final Parser parser;
    private final ObservableList<GraphableFunc> userFunctions;

    
    public Controller() {
        this.output = new SimpleStringProperty("");
        this.parser = new Parser();
        this.userFunctions = FXCollections.observableArrayList();
    }

    // FXML created objects, only import the objects
    // we actually need to control/modify
    @FXML private Label display;        // handles the actual display/output for our calculator
    @FXML private TableView<GraphableFunc> userFuncTable;
    @FXML private TableColumn<GraphableFunc, String> indexCol;
    @FXML private TableColumn<GraphableFunc, String> functionCol;
    @FXML private TableColumn<GraphableFunc, Boolean> checkBoxCol;
    @FXML private ToggleButton graphToggleButton, normToggleButton;
    @FXML private ToggleGroup modeControl;
    @FXML private VBox normalModePane;
    @FXML private SplitPane graphModePane;
    @FXML private LineChart<Double, Double> graphChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private Slider negXSlider, negYSlider, posXSlider, posYSlider;

    
    @FXML void buttonHandler(ActionEvent e) {
        String key = ((Button) e.getSource()).getText();
        processInput(key);
    }

   
    private void processInput(String key) {
        String curr = output.get(); // grab existing output for efficiency
        // check for error string and clear it.
        if (curr.matches("Error|Undefined")) {
            output.set("");
        }
        if (key.matches("[=]")) {               // match equals/enter first
            computeNow();
        } else if (key.matches("C|CE")) {       // catch clear before generic alpha catching for functions
            output.set("");
        } else if (key.matches("⇍")) {
            if (noEntry()) {
                return;
            } else {
                output.set(curr.substring(0, curr.length() - 1));
            }
        } else if (key.matches("\\+/-")) {      // handle 'invert' sign: i.e. "+/-" key
            if (noEntry()) {
                return;
            } else if (curr.charAt(0) == '-') {
                output.set(curr.substring(1)); // remove the leading '-'
            } else {
                output.set("-(" + curr + ")"); // negate the current input & wrap in parens to be safe
            }
        } else if (key.matches("1/x")) {        // handle reciprocal key
            output.set("1/(" + curr + ")" );
            computeNow();
        } else {
            output.set(curr + key);
        }
    }

   
    private boolean noEntry() {
        return output.get().isEmpty() || output.get().isBlank();
    }

   
    private void computeNow() {
        try {
            Expression x = parser.eval(output.get());
            Double result = x.eval();
            if (result.isNaN()) {   // make sure we actually have a number
                output.set("Undefined");
            } else {
                output.set(String.valueOf(result));
            }
        } catch (Exception e) {
            output.set("Error");
        }
    }

    
    private void keyHandler(KeyEvent e) {
        KeyCode key = e.getCode();
        switch (key) {
            case ENTER:
                processInput("="); break;
            case DELETE:    // intentionally fall through
            case BACK_SPACE:
                processInput("⇍"); break;
            default:
                processInput(e.getText()); break;
        }
    }

    
    private void parseFuncInput(CellEditEvent<GraphableFunc, String> e) {
        GraphableFunc func = userFuncTable.getSelectionModel().getSelectedItem();
        String raw = e.getNewValue();
        func.setRawInput(raw);
        if (userFunctions.size() == func.getIndex() + 1) {
            // add a new row/blank entry
            this.addFunctionRow();
        }
        this.graphNow(func);
    }

    
    private void graphNow(GraphableFunc func) {
        Series<Double, Double> data = func.getData();  // get the data series from the function object
        if (!graphChart.getData().contains(data)) {     // new function, add the data series to the chart
            graphChart.getData().add(data);     // add the data series to the chart
            data.getNode().visibleProperty().bindBidirectional(func.checkedProperty());
        } else {
            data.getData().clear(); // clear the data series and recompute
        }
        HashMap<String, Double> vars = new HashMap<>();
        Expression exp = parser.eval(func.getRawInput(), vars);
        func.setExpression(exp);    // store the compiled expression for reuse
	    double incr;
	    if (isTrigFunc(func.getRawInput())) {
	    	incr = Math.PI/18;  // use incrememts of PI for trig functions
	    } else {
	    	incr = 0.1; // else use increments of 1/10th
	    }
        for (double i = -100; i <= 100; i+=incr) {   // make sure we have decent 'resolution'
            vars.put(func.getVarName(), i);
            double yVal = exp.eval();
            data.getData().add(new Data<>(i, yVal));
        }
    }

    private boolean isTrigFunc(String in) {
	    Pattern p = Pattern.compile("sin|cos|tan|sec|csc|cot");
	    return p.matcher(in).find();
    }

   
    private void addFunctionRow() {
        userFunctions.add(new GraphableFunc(userFunctions.size()));
    }

    
    private void initTable() {
        checkBoxCol.setCellValueFactory(cellData -> cellData.getValue().checkedProperty());
        checkBoxCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        indexCol.setCellValueFactory(cellData -> Bindings.format("y%d=", cellData.getValue().indexProperty()));
        functionCol.setCellValueFactory(cellData -> cellData.getValue().rawInputProperty());
        functionCol.setCellFactory(TextFieldTableCell.forTableColumn());
        functionCol.setOnEditCommit(this::parseFuncInput);
        userFunctions.add(new GraphableFunc(userFunctions.size())); // add the first entry row
        userFuncTable.setItems(userFunctions);
    }

    
    private void initGraph() {
        // bind the slider positions to relevant axis upper/lower bounds
        xAxis.lowerBoundProperty().bind(negXSlider.valueProperty());
        xAxis.upperBoundProperty().bind(posXSlider.valueProperty());
        yAxis.lowerBoundProperty().bind(negYSlider.valueProperty());
        yAxis.upperBoundProperty().bind(posYSlider.valueProperty());
        // set the label formatter for our axis to only show ints
        xAxis.setTickLabelFormatter(new AxisFormatter());
        yAxis.setTickLabelFormatter(new AxisFormatter());
    }

   
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // bind our output display/label
        this.display.textProperty().bind(this.output);
        // hook the pane to register our keyHandler
        this.normalModePane.setOnKeyPressed(this::keyHandler);
        // bind our mode toggle buttons
        this.normToggleButton.selectedProperty().bindBidirectional(normalModePane.visibleProperty());
        this.graphToggleButton.selectedProperty().bindBidirectional(graphModePane.visibleProperty());
        // initialize the graph input table
        this.initTable();
        // initialize the actual graph/chart
        this.initGraph();
        // select normal mode by default on launch
        this.modeControl.selectToggle(graphToggleButton);
        // ensure we never end up without a mode selected
        modeControl.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                modeControl.selectToggle(oldVal);
            }
        });
    }
}
