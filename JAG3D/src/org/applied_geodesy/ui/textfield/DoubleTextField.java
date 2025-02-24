/***********************************************************************
* Copyright by Michael Loesler, https://software.applied-geodesy.org   *
*                                                                      *
* This program is free software; you can redistribute it and/or modify *
* it under the terms of the GNU General Public License as published by *
* the Free Software Foundation; either version 3 of the License, or    *
* at your option any later version.                                    *
*                                                                      *
* This program is distributed in the hope that it will be useful,      *
* but WITHOUT ANY WARRANTY; without even the implied warranty of       *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        *
* GNU General Public License for more details.                         *
*                                                                      *
* You should have received a copy of the GNU General Public License    *
* along with this program; if not, see <http://www.gnu.org/licenses/>  *
* or write to the                                                      *
* Free Software Foundation, Inc.,                                      *
* 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.            *
*                                                                      *
***********************************************************************/

package org.applied_geodesy.ui.textfield;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.applied_geodesy.util.CellValueType;
import org.applied_geodesy.util.FormatterChangedListener;
import org.applied_geodesy.util.FormatterEvent;
import org.applied_geodesy.util.FormatterOptions;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;

public class DoubleTextField extends TextField implements FormatterChangedListener {
	public enum ValueSupport {
		INCLUDING_INCLUDING_INTERVAL,
		INCLUDING_EXCLUDING_INTERVAL,
		EXCLUDING_INCLUDING_INTERVAL,
		EXCLUDING_EXCLUDING_INTERVAL,

		NULL_VALUE_SUPPORT,
		NON_NULL_VALUE_SUPPORT;
	}
	
	private FormatterOptions options = FormatterOptions.getInstance();
	private final static int EDITOR_ADDITIONAL_DIGITS = 10;
	private double lowerBoundary = Double.NEGATIVE_INFINITY, upperBoundary = Double.POSITIVE_INFINITY;
	private NumberFormat editorNumberFormat;
	private CellValueType type;
	private final boolean displayUnit;
	private final ValueSupport valueSupport;
	private ObjectProperty<Double> number = new SimpleObjectProperty<>();
	private boolean typeChanged = false;
	public DoubleTextField(CellValueType type) {
		this(null, type, false);
	}
	
	public DoubleTextField(CellValueType type, boolean displayUnit) {
		this(null, type, displayUnit);
	}

	public DoubleTextField(Double value, CellValueType type, boolean displayUnit) {
		this(value, type, displayUnit, ValueSupport.NULL_VALUE_SUPPORT);
	}
	
	public DoubleTextField(Double value, CellValueType type, boolean displayUnit, ValueSupport valueSupport) {
		this(value, type, displayUnit, valueSupport, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}
	
	public DoubleTextField(Double value, CellValueType type, boolean displayUnit, ValueSupport valueSupport, double lowerBoundary, double upperBoundary) {
		super();		
		
		this.type = type;
		this.valueSupport = valueSupport;
		this.displayUnit  = displayUnit;
		this.lowerBoundary = lowerBoundary;
		this.upperBoundary = upperBoundary;

		if (this.check(value))
			this.setNumber(value);
		
		this.prepareEditorNumberFormat();
		this.initHandlers();
		this.setTextFormatter(this.createTextFormatter());
		
		if (this.check(value))
			this.setText(this.getRendererFormat(value));
		
		this.options.addFormatterChangedListener(this);
	}
	
	public void setCellValueType(CellValueType type) {
		this.type = type;
		this.typeChanged = true;
		
		double value = this.getNumber();
		
		if (this.check(value))
			this.setNumber(value);
		
		this.prepareEditorNumberFormat();
		this.setTextFormatter(this.createTextFormatter());
		
		if (this.check(value))
			this.setText(this.getRendererFormat(value));
	}
	
	private void prepareEditorNumberFormat() {
		this.editorNumberFormat = (NumberFormat)this.options.getFormatterOptions().get(this.type).getFormatter().clone();
		this.editorNumberFormat.setMinimumFractionDigits(this.editorNumberFormat.getMaximumFractionDigits());
		this.editorNumberFormat.setMaximumFractionDigits(this.editorNumberFormat.getMaximumFractionDigits() + EDITOR_ADDITIONAL_DIGITS);
	}
	
	public boolean check(Double value) {
		switch(this.valueSupport) {
		case INCLUDING_INCLUDING_INTERVAL:
			return value != null && this.lowerBoundary <= value.doubleValue() && value.doubleValue() <= this.upperBoundary;
		case EXCLUDING_EXCLUDING_INTERVAL:
			return value != null && this.lowerBoundary <  value.doubleValue() && value.doubleValue() <  this.upperBoundary;
		case INCLUDING_EXCLUDING_INTERVAL:
			return value != null && this.lowerBoundary <= value.doubleValue() && value.doubleValue() <  this.upperBoundary;
		case EXCLUDING_INCLUDING_INTERVAL:
			return value != null && this.lowerBoundary <  value.doubleValue() && value.doubleValue() <= this.upperBoundary;
		case NON_NULL_VALUE_SUPPORT:
			return value != null;
		default: // NULL_VALUE_SUPPORT:
			return true;
		}
	}

	private TextFormatter<Double> createTextFormatter() {
		//Pattern decimalPattern = Pattern.compile("^[-|+]?\\d*?\\D*\\d{0,"+this.editorNumberFormat.getMaximumFractionDigits()+"}\\s*\\D*$");
		Pattern decimalPattern = Pattern.compile("^[+|-]?[\\d\\D]*?\\d{0,"+this.editorNumberFormat.getMaximumFractionDigits()+"}\\s*\\D*$");
		UnaryOperator<TextFormatter.Change> filter = new UnaryOperator<TextFormatter.Change>() {

			@Override
			public Change apply(TextFormatter.Change change) {
				if (!change.isContentChange())
		            return change;

				try {
					String input = change.getControlNewText();
					if (input == null || input.trim().isEmpty() || decimalPattern.matcher(input.trim()).matches())
						return change;

				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
		
		return new TextFormatter<Double>(filter);
	}

	private void initHandlers() {

//		this.setOnAction(new EventHandler<ActionEvent>() {
//			@Override
//			public void handle(ActionEvent event) {
//				parseAndFormatInput();
//			}
//		});
		// https://docs.oracle.com/javase/8/javafx/api/javafx/scene/Node.html#setEventHandler-javafx.event.EventType-javafx.event.EventHandler-
		// ActionEvent is called before setOnAction()
		this.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				parseAndFormatInput();
			}
		});

		this.focusedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (typeChanged) {
					typeChanged = false;
					setText(getEditorFormat(getNumber()));
				}
				if (!newValue.booleanValue()) {
					parseAndFormatInput();
					setText(getRendererFormat(getNumber()));
				}
				else {
					setText(getEditorFormat(getNumber()));
				}
			}
		});

		numberProperty().addListener(new ChangeListener<Double>() {
			@Override
			public void changed(ObservableValue<? extends Double> obserable, Double oldValue, Double newValue) {
				setText(getEditorFormat(getNumber()));
				//setText(getRendererFormat(newValue));
			}
		});
	}
	
	private String getEditorFormat(Double value) {
		if (!this.check(value))
			return null;

		value = this.getNumber();
		if (value == null)
			return null;

		switch(this.type) {
		case ANGLE:
			return this.editorNumberFormat.format(this.options.convertAngleToView(value.doubleValue()));
			
		case ANGLE_RESIDUAL:
			return this.editorNumberFormat.format(this.options.convertAngleResidualToView(value.doubleValue()));

		case ANGLE_UNCERTAINTY:
			return this.editorNumberFormat.format(this.options.convertAngleUncertaintyToView(value.doubleValue()));

		case LENGTH:
			return this.editorNumberFormat.format(this.options.convertLengthToView(value.doubleValue()));

		case LENGTH_RESIDUAL:
			return this.editorNumberFormat.format(this.options.convertLengthResidualToView(value.doubleValue()));

		case LENGTH_UNCERTAINTY:
			return this.editorNumberFormat.format(this.options.convertLengthUncertaintyToView(value.doubleValue()));
	
		case SCALE:
			return this.editorNumberFormat.format(this.options.convertScaleToView(value.doubleValue()));
			
		case SCALE_RESIDUAL:
			return this.editorNumberFormat.format(this.options.convertScaleResidualToView(value.doubleValue()));

		case SCALE_UNCERTAINTY:
			return this.editorNumberFormat.format(this.options.convertScaleUncertaintyToView(value.doubleValue()));
			
		case STATISTIC:
		case DOUBLE:
			return this.editorNumberFormat.format(value.doubleValue());

		case VECTOR:
			return this.editorNumberFormat.format(this.options.convertVectorToView(value.doubleValue()));
			
		case VECTOR_RESIDUAL:
			return this.editorNumberFormat.format(this.options.convertVectorResidualToView(value.doubleValue()));

		case VECTOR_UNCERTAINTY:
			return this.editorNumberFormat.format(this.options.convertVectorUncertaintyToView(value.doubleValue()));
			
		case TEMPERATURE:
			return this.editorNumberFormat.format(this.options.convertTemperatureToView(value.doubleValue()));
			
		case PRESSURE:
			return this.editorNumberFormat.format(this.options.convertPressureToView(value.doubleValue()));
			
		case PERCENTAGE:
			return this.editorNumberFormat.format(this.options.convertPercentToView(value.doubleValue()));

		default:
			return this.editorNumberFormat.format(value.doubleValue());
		}
	}
	
	String getRendererFormat(Double value) {
		if (!this.check(value))
			return null;
		
		value = this.getNumber();
		if (value == null)
			return null;
		
		switch(this.type) {
		case ANGLE:
			return this.options.toAngleFormat(value.doubleValue(), this.displayUnit);
			
		case ANGLE_RESIDUAL:
			return this.options.toAngleResidualFormat(value.doubleValue(), this.displayUnit);

		case ANGLE_UNCERTAINTY:
			return this.options.toAngleUncertaintyFormat(value.doubleValue(), this.displayUnit);

		case LENGTH:
			return this.options.toLengthFormat(value.doubleValue(), this.displayUnit);

		case LENGTH_RESIDUAL:
			return this.options.toLengthResidualFormat(value.doubleValue(), this.displayUnit);

		case LENGTH_UNCERTAINTY:
			return this.options.toLengthUncertaintyFormat(value.doubleValue(), this.displayUnit);

		case SCALE:
			return this.options.toScaleFormat(value.doubleValue(), this.displayUnit);
			
		case SCALE_RESIDUAL:
			return this.options.toScaleResidualFormat(value.doubleValue(), this.displayUnit);

		case SCALE_UNCERTAINTY:
			return this.options.toScaleUncertaintyFormat(value.doubleValue(), this.displayUnit);

		case STATISTIC:
			return this.options.toStatisticFormat(value.doubleValue());

		case VECTOR:
			return this.options.toVectorFormat(value.doubleValue(), this.displayUnit);
			
		case VECTOR_RESIDUAL:
			return this.options.toVectorResidualFormat(value.doubleValue(), this.displayUnit);

		case VECTOR_UNCERTAINTY:
			return this.options.toVectorUncertaintyFormat(value.doubleValue(), this.displayUnit);
			
		case TEMPERATURE:
			return this.options.toTemperatureFormat(value.doubleValue(), this.displayUnit);
			
		case PRESSURE:
			return this.options.toPressureFormat(value.doubleValue(), this.displayUnit);
			
		case PERCENTAGE:
			return this.options.toPercentFormat(value.doubleValue(), this.displayUnit);
			
		case DOUBLE:
			return this.options.toDoubleFormat(value.doubleValue());

		default:
			return null;
		}
	}

	/**
	 * Tries to parse the user input to a number according to the provided
	 * NumberFormat
	 */
	private void parseAndFormatInput() {
		try {
			String input = this.getText();
			if (input != null && !input.trim().isEmpty()) {
				input = input.replaceAll(",", ".");
				ParsePosition parsePosition = new ParsePosition(0);
				Double newValue = this.options.getFormatterOptions().get(this.type).parse(input, parsePosition).doubleValue();
				// check if value is not null and if the complete value is error-free parsed 
				// https://www.ibm.com/developerworks/library/j-numberformat/index.html
				// https://stackoverflow.com/questions/14194888/validating-decimal-numbers-in-a-locale-sensitive-way-in-java
				if (newValue != null && parsePosition.getErrorIndex() < 0 && parsePosition.getIndex() == input.length()) {
					switch(this.type) {
					case ANGLE:
						newValue = this.options.convertAngleToModel(newValue.doubleValue());
						break;
					case ANGLE_RESIDUAL:
						newValue = this.options.convertAngleResidualToModel(newValue.doubleValue());
						break;
					case ANGLE_UNCERTAINTY:
						newValue = this.options.convertAngleUncertaintyToModel(newValue.doubleValue());
						break;
					case LENGTH:
						newValue = this.options.convertLengthToModel(newValue.doubleValue());
						break;
					case LENGTH_RESIDUAL:
						newValue = this.options.convertLengthResidualToModel(newValue.doubleValue());
						break;
					case LENGTH_UNCERTAINTY:
						newValue = this.options.convertLengthUncertaintyToModel(newValue.doubleValue());
						break;
					case SCALE:
						newValue = this.options.convertScaleToModel(newValue.doubleValue());
						break;
					case SCALE_RESIDUAL:
						newValue = this.options.convertScaleResidualToModel(newValue.doubleValue());
						break;
					case SCALE_UNCERTAINTY:
						newValue = this.options.convertScaleUncertaintyToModel(newValue.doubleValue());
						break;
					case DOUBLE:
					case STATISTIC:
						newValue = newValue.doubleValue();
						break;
					case VECTOR:
						newValue = this.options.convertVectorToModel(newValue.doubleValue());
						break;
					case VECTOR_RESIDUAL:
						newValue = this.options.convertVectorResidualToModel(newValue.doubleValue());
						break;
					case VECTOR_UNCERTAINTY:
						newValue = this.options.convertVectorUncertaintyToModel(newValue.doubleValue());
						break;
					case TEMPERATURE:
						newValue = this.options.convertTemperatureToModel(newValue.doubleValue());
						break;
					case PRESSURE:
						newValue = this.options.convertPressureToModel(newValue.doubleValue());
						break;
					case PERCENTAGE:
						newValue = this.options.convertPercentToModel(newValue.doubleValue());
						break;
					default:
						newValue = newValue.doubleValue();
						break;
					}
					// set new value, if valid
					this.setNumber(!this.check(newValue) ? this.getNumber() : newValue);
				}
			}
			else if ((input == null || input.trim().isEmpty()) && this.check(null)) {
				this.setNumber(null);
			}
			this.selectAll();
			
		} catch (Exception ex) {
			this.setText(this.getRendererFormat(this.getNumber()));
		}
	}
	
	public final Double getNumber() {
		return this.number.get();
	}

	public final void setNumber(Double value) {
		this.number.set(value);
	}
	
	public final void setValue(Double value) {
		this.number.set(value);
		this.setText(this.getRendererFormat(value));
	}

	public ObjectProperty<Double> numberProperty() {
		return this.number;
	}
	
	public CellValueType getCellValueType() {
		return this.type;
	}
	
	public boolean isDisplayUnit() {
		return this.displayUnit;
	}
	
	public ValueSupport getValueSupport() {
		return this.valueSupport;
	}

	@Override
	public void formatterChanged(FormatterEvent evt) {
		this.prepareEditorNumberFormat();
		this.setText(this.getRendererFormat(this.getNumber()));
	}
}