package com.example.parrot;

import android.widget.SectionIndexer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Provides fast-scroll A-Z section indexing for ListView and GridView adapters.
 */
public final class AlphabetSectionIndexer implements SectionIndexer {

    private final String[] sections;
    private final int[] sectionToPosition;
    private final int[] positionToSection;

    public AlphabetSectionIndexer(String[] titles) {
        int count = (titles != null) ? titles.length : 0;
        positionToSection = new int[count];
        ArrayList<String> secList = new ArrayList<>();
        ArrayList<Integer> posList = new ArrayList<>();

        String lastLetter = null;
        for (int i = 0; i < count; i++) {
            String letter = extractInitial(titles[i]);
            if (!letter.equals(lastLetter)) {
                secList.add(letter);
                posList.add(i);
                lastLetter = letter;
            }
            positionToSection[i] = secList.size() - 1;
        }

        sections = secList.toArray(new String[0]);
        sectionToPosition = new int[posList.size()];
        for (int i = 0; i < posList.size(); i++) {
            sectionToPosition[i] = posList.get(i);
        }
    }

    private static String extractInitial(String title) {
        if (title == null) return "#";
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return "#";
        char c = Character.toUpperCase(trimmed.charAt(0));
        if (c >= 'A' && c <= 'Z') {
            return String.valueOf(c);
        }
        return "#";
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sectionToPosition.length == 0) return 0;
        int clamped = Math.max(0, Math.min(sectionIndex, sectionToPosition.length - 1));
        return sectionToPosition[clamped];
    }

    @Override
    public int getSectionForPosition(int position) {
        if (positionToSection.length == 0) return 0;
        int clamped = Math.max(0, Math.min(position, positionToSection.length - 1));
        return positionToSection[clamped];
    }
}
