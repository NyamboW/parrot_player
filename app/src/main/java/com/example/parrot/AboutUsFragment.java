package com.example.parrot;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AboutUsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AboutUsFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public AboutUsFragment() {
        // Required empty public constructor
    }
    Button btnContact;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment AboutUsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static AboutUsFragment newInstance(String param1, String param2) {
        AboutUsFragment fragment = new AboutUsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_about_us, container, false);

        btnContact = view.findViewById(R.id.btnContact);
        View btnWebsite = view.findViewById(R.id.btnWebsite);
        View btnPlaystore = view.findViewById(R.id.btnPlaystore);
        android.widget.TextView txtAboutAuthor = view.findViewById(R.id.txtAboutAuthor);
        android.widget.ImageView imgAboutWebsite = view.findViewById(R.id.imgAboutWebsite);
        android.widget.ImageView imgAboutPlaystore = view.findViewById(R.id.imgAboutPlaystore);

        int accent = AccentColorHelper.getAccentColor(requireContext());
        if (txtAboutAuthor != null) txtAboutAuthor.setTextColor(accent);
        if (imgAboutWebsite != null) {
            imgAboutWebsite.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        if (imgAboutPlaystore != null) {
            imgAboutPlaystore.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        if (btnContact != null) {
            float density = getResources().getDisplayMetrics().density;
            btnContact.setBackground(AccentColorHelper.createRoundedRect(accent, 0, 0, 12f * density));
        }

        btnWebsite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/misterw"));
                startActivity(browserIntent);
            }
        });

        btnPlaystore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent playstoreIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=POINTBLANK"));
                startActivity(playstoreIntent);
            }
        });

        /*
        button for contact with developer
         */
        btnContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto","iamwestoby@gmail.com", null));
                String subject="PARROT AUDIO PLAYER";
                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                String message="";
                intent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(intent, "Choose an Email client :"));
            }
        });

        return view;
    }
}