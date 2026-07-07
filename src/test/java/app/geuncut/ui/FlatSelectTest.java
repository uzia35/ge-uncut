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
}
