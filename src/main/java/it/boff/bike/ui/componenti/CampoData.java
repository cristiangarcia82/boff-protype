package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.datepicker.DatePicker;

import java.util.List;
import java.util.Locale;

/** DatePicker in italiano (nomi di mesi e giorni, settimana che inizia di lunedì). */
public final class CampoData {

    private CampoData() {
    }

    public static DatePicker crea(String etichetta) {
        DatePicker dp = new DatePicker(etichetta);
        dp.setLocale(Locale.ITALY);
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setMonthNames(List.of("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
                "agosto", "settembre", "ottobre", "novembre", "dicembre"));
        i18n.setWeekdays(List.of("domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"));
        i18n.setWeekdaysShort(List.of("dom", "lun", "mar", "mer", "gio", "ven", "sab"));
        i18n.setFirstDayOfWeek(1);
        i18n.setToday("Oggi");
        i18n.setCancel("Annulla");
        i18n.setDateFormat("dd/MM/yyyy");
        dp.setI18n(i18n);
        return dp;
    }
}
