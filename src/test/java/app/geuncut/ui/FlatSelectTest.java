package app.geuncut.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlatSelectTest {
	@Test
	public void selectedValueReturnsTheApiValueNotTheLabel() {
		FlatSelect select = new FlatSelect(
				new String[] { "Standard", "Fast Fill", "High Volume" },
				new String[] { "standard", "fast", "value" }, 1);
		assertEquals("fast", select.selectedValue());
	}

	@Test
	public void singleArgConstructorUsesOptionsAsValues() {
		FlatSelect select = new FlatSelect(new String[] { "Conservative", "Balanced", "Aggressive" }, 2);
		assertEquals("Aggressive", select.selectedValue());
	}

	@Test
	public void setOptionsKeepsTheSelectionWhenItsValueSurvives() {
		FlatSelect select = new FlatSelect(
				new String[] { "1M gp", "10M gp" },
				new String[] { "1000000", "10000000" }, 1);
		select.setOptions(
				new String[] { "My capital (5.0M gp)", "1M gp", "10M gp" },
				new String[] { "", "1000000", "10000000" },
				select.selectedValue());
		assertEquals("10000000", select.selectedValue());
	}

	@Test
	public void setOptionsFallsToTheFirstOptionWhenTheValueIsGone() {
		FlatSelect select = new FlatSelect(
				new String[] { "My capital (5.0M gp)", "1M gp" },
				new String[] { "", "1000000" }, 0);
		select.setOptions(
				new String[] { "1M gp", "10M gp" },
				new String[] { "1000000", "10000000" },
				select.selectedValue());
		assertEquals("1000000", select.selectedValue());
	}
}
