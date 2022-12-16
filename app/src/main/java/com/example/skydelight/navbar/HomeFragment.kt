package com.example.skydelight.navbar

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.skydelight.BuildConfig
import com.example.skydelight.R
import com.example.skydelight.custom.AppDatabase
import com.example.skydelight.custom.ElementsEditor
import com.example.skydelight.custom.ValidationsDialogsRequests
import com.example.skydelight.custom.ViewPageAdapter
import com.example.skydelight.databinding.FragmentNavbarHomeBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class HomeFragment : Fragment() {

    // Binding variable to use elements in the xml layout
    private lateinit var binding : FragmentNavbarHomeBinding

    // Creating the fragment view
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        binding = FragmentNavbarHomeBinding.inflate(inflater, container, false)
        return binding.root

        // return inflater.inflate(R.layout.fragment_navbar_home, container, false)
    }

    private var isLoadingAdvice = false

    // After the view is created we can do things
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Showing initial random advice with reload icon
        showAdvice()

        // Loading pictures on view pager and connecting it with dots tab layout
        try {
            binding.viewPagerMain.adapter = ViewPageAdapter(requireContext(), null, 2)
            binding.tabLayout.setupWithViewPager(binding.viewPagerMain, true)
        } catch(e: java.lang.IllegalStateException) {}

        // Enabling links
        Linkify.addLinks(binding.txtCredits, Linkify.WEB_URLS)
        binding.txtCredits.movementMethod = LinkMovementMethod.getInstance()

        // Pictures Actions
        var isAdviceLayout = true
        binding.viewPagerMain.addOnPageChangeListener ( object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                // Change to Status View
                if(isAdviceLayout) {
                    binding.adviceLayout.visibility = View.GONE
                    binding.statusLayout.visibility = View.VISIBLE
                    isAdviceLayout = false

                    // Hide reload if isn't loading advice
                    if(!isLoadingAdvice)
                        updateImgReloadFromParent(false)
                }
                // Change to Advice View
                else {
                    binding.adviceLayout.visibility = View.VISIBLE
                    binding.statusLayout.visibility = View.GONE
                    isAdviceLayout = true

                    // Show reload if isn't loading advice
                    if(!isLoadingAdvice)
                        updateImgReloadFromParent(true)
                }
            }
        })
    }

    private fun updateImgReloadFromParent(state: Boolean) {
        if(parentFragment is NavBarFragment)
            (parentFragment as NavBarFragment).updateImgReload(state)
        else {
            val hostFragment = parentFragment as NavHostFragment
            (hostFragment.parentFragment as NavBarFragment).updateImgReload(state)
        }
    }

    // Function to connect with the api
    fun showAdvice() {
        // Changing texts and hiding image reload
        isLoadingAdvice = true
        updateImgReloadFromParent(false)
        binding.txtTitle.text = getString(R.string.loading)
        binding.textView.text = getString(R.string.loading)
        binding.txtCredits.text = getString(R.string.loading)

        // Launching room database connection
        MainScope().launch {
            try {
                // Creating connection to database
                val userDao = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "user")
                    .fallbackToDestructiveMigration().build().userDao()
                val user = userDao.getUser()[0]

                // Making http request
                var request = Request.Builder().url("https://apiskydelight.herokuapp.com/api/lista-testsqv-personal/")
                    .addHeader("Authorization", "Bearer " + user.token)
                    .addHeader("KEY-CLIENT", BuildConfig.API_KEY)
                    .post(FormBody.Builder().add("email", user.email).build()).build()

                if(isAdded) {
                    ValidationsDialogsRequests().httpPetition(request, requireContext(), requireView(),
                        requireActivity(),null, null, null, null, null,
                        null, 500,null, null) {
                        // Changing http body to json
                        val arrayString = JSONObject(it).getString("data")

                        // Arguments to Post Request
                        val formBody = FormBody.Builder()

                        // If user has answered at least 1 SVQ test
                        if(arrayString != "[]" && user.advice) {
                            // Getting last SVQ test answered
                            val arrayObject = JSONArray(arrayString).getJSONObject(JSONArray(arrayString).length()-1)
                            val questionsList = ArrayList<Int>()

                            // Saving bad answers from user
                            for(i in 1..20)
                                if(arrayObject.getInt("pregunta$i") < 4)
                                    questionsList.add(i)

                            // Preparing selected answers to http petition
                            if(questionsList.size < 20)
                                formBody.add("type_advice", "especifico")
                                        .add("list_excluded", questionsList.toString())
                            else
                                formBody.add("type_advice", "general")
                                        .add("list_excluded", "[]")
                        } else
                            formBody.add("type_advice", "general")
                                    .add("list_excluded", "[]")

                        // Making http request
                        request = Request.Builder().url("https://apiskydelight.herokuapp.com/api/consejo")
                            .post(formBody.build()).build()

                        // Toggle advice boolean
                        user.advice = !user.advice

                        // Launching room database connection
                        MainScope().launch { userDao.updateUser(user) }

                        if(isAdded) {
                            try {
                                ValidationsDialogsRequests().httpPetition(request, requireContext(), requireView(),
                                    requireActivity(),null, null, null, null, null,
                                    null, 500,null, null) {
                                    // Changing http body to json and changing advice text
                                    MainScope().launch {
                                        binding.txtTitle.text = "Recomendación\n${JSONObject(it).getString("id")} de 50"
                                        binding.textView.text = JSONObject(it).getString("consejo")
                                        isLoadingAdvice = false

                                        // Show reload only on advice layout
                                        if(binding.adviceLayout.visibility == View.VISIBLE)
                                            updateImgReloadFromParent(true)

                                        // Show random background
                                        showBackground()
                                    }
                                }
                            } catch(e: IllegalStateException) {}
                        }
                    }
                }
            } catch(e: java.lang.IllegalStateException) {}
        }
    }

    // Getting wallpaper from pexels API
    private fun showBackground() {
        val pageNumber = (1..8000).shuffled().last()
        val request = Request.Builder()
            .url("https://api.pexels.com/v1/search?query=nature wallpaper&orientation=portrait&per_page=1&size=small&page=$pageNumber")
            .addHeader("Authorization", BuildConfig.API_KEY_PEXELS).get().build()

        try {
            ValidationsDialogsRequests().httpPetition(request, requireContext(), requireView(),
                requireActivity(),null, null, null, null, null,
                null, null,null, null) {

                // Getting data
                val imageUrlData = JSONObject(it).getString("photos")

                // Getting image url
                val imageUrl = imageUrlData.substringAfter("\"portrait\":\"")
                    .substringBefore("\"").replace("\\", "")

                // Getting photographer name
                val photographerName = imageUrlData.substringAfter("\"photographer\":\"")
                    .substringBefore("\"")

                // Getting photographer profile link
                val photographerProfile = imageUrlData.substringAfter("\"photographer_url\":\"")
                    .substringBefore("\"").replace("\\", "")

                // Creating photo, pexels and photographer links
                val photoUrl = "<a href=\"$imageUrl\">Photo</a>"
                val pexelsUrl = "<a href=\"https://www.pexels.com/\">Pexels</a>"
                val photographerUrl = "<a href=\"$photographerProfile\">$photographerName</a>"

                // Loading image
                MainScope().launch {
                    try {
                        // Showing photo, photographer and pexels links
                        binding.txtCredits.text = Html.fromHtml("$photoUrl by $photographerUrl\non $pexelsUrl",
                            Html.FROM_HTML_MODE_COMPACT)
                        binding.txtCredits.setLinkTextColor(ElementsEditor().getColor(context,
                            com.google.android.material.R.attr.colorSecondaryVariant))

                        // Loading image from api
                        Glide.with(requireContext())
                            .load(imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(true)
                            .dontAnimate()
                            .dontTransform()
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .priority(Priority.IMMEDIATE)
                            .format(DecodeFormat.DEFAULT)
                            .into(binding.imgBackground)
                    } catch(e: java.lang.IllegalStateException) {}
                }
            }
        } catch(e: java.lang.IllegalStateException) {}
    }
}