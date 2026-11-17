package com.example.parrot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class AboutUsFragment extends Fragment {

    public AboutUsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about_us, container, false);

        TextView txtByCompany = view.findViewById(R.id.txtByCompany);
        TextView txtAboutAuthor = view.findViewById(R.id.txtAboutAuthor);
        TextView txtAboutFeaturesTitle = view.findViewById(R.id.txtAboutFeaturesTitle);
        TextView txtFooterBrand = view.findViewById(R.id.txtFooterBrand);

        int accent = AccentColorHelper.getAccentColor(requireContext());
        if (txtByCompany != null) txtByCompany.setTextColor(accent);
        if (txtAboutAuthor != null) txtAboutAuthor.setTextColor(accent);
        if (txtAboutFeaturesTitle != null) txtAboutFeaturesTitle.setTextColor(accent);
        if (txtFooterBrand != null) txtFooterBrand.setTextColor(accent);

        return view;
    }
}
