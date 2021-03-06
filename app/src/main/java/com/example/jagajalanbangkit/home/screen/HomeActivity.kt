package com.example.jagajalanbangkit.home.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.auth0.android.jwt.JWT
import com.example.jagajalanbangkit.MyApplication
import com.example.jagajalanbangkit.R
import com.example.jagajalanbangkit.admin.home.screen.AdminHomeActivity
import com.example.jagajalanbangkit.databinding.ActivityHomeBinding
import com.example.jagajalanbangkit.lapor.screen.LaporActivity
import com.example.jagajalanbangkit.login.screen.LoginActivity
import com.example.jagajalanbangkit.riwayat.screen.RiwayatActivity
import com.example.jagajalanbangkit.viewmodels.LaporanViewModel
import com.example.jagajalanbangkit.viewmodels.UserViewModel
import com.example.jagajalanbangkit.viewmodels.ViewModelFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var googleMap: GoogleMap

    @Inject
    lateinit var factory: ViewModelFactory

    private val laporanViewModel: LaporanViewModel by viewModels{
        factory
    }

    private val userViewModel: UserViewModel by viewModels{
        factory
    }

    private var locationManager : LocationManager? = null

    private lateinit var sharedPreferences: SharedPreferences

    private var listLaporan : List<ArrayList<Double>>? = null

    private lateinit var mapFragment : SupportMapFragment

    private var role : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        MyApplication.appComponent.inject(this)
        testToken()
        GlobalScope.launch(Dispatchers.Main) {
            getAllLaporan()
        }
        binding = ActivityHomeBinding.inflate(layoutInflater)
        binding.progressBar.visibility = View.VISIBLE
        checkLocation()
        if (ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@HomeActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this@HomeActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this@HomeActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)

        binding.progressBar.visibility = View.INVISIBLE

        setClickListener()
        setContentView(binding.root)

        binding.apply {
            btnLapor.setOnClickListener {
                val intent = Intent(this@HomeActivity, LaporActivity::class.java)
                startActivity(intent)
            }

            btnRiwayat.setOnClickListener {
                val intent = Intent(this@HomeActivity, RiwayatActivity::class.java)
                startActivity(intent)
            }
        }

    }

    private fun setClickListener(){
        val token = getPreferences(Context.MODE_PRIVATE).getString(getString(R.string.key_token), "invalid")
        val refreshToken = getPreferences(Context.MODE_PRIVATE).getString(getString(R.string.key_refresh_token), "invalid")
        binding.btnLapor.setOnClickListener {
            try {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener)
            } catch(ex: SecurityException) {
            }

            val intent = Intent(this, LaporActivity::class.java)
            intent.putExtra("token", token)
            intent.putExtra("refreshToken", refreshToken)
            startActivity(intent)
        }
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            GlobalScope.async {
                with(sharedPreferences.edit()){
                    putString(getString(R.string.key_latitude), location.latitude.toString())
                    putString(getString(R.string.key_longitude), location.longitude.toString())
                    commit()
                }
            }
        }
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun checkLocation(){
        if(!locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Toast.makeText(this@HomeActivity, "Nyalakan GPS untuk menggunakan JagaJalan", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun getAllLaporan(){
        val wait = GlobalScope.async {
            listLaporan = laporanViewModel.getAllLaporan()

            mapFragment = supportFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment
            listLaporan
        }
        println(wait.await())
        mapFragment.getMapAsync(OnMapReadyCallback {
            if (listLaporan != null) {
                for (koordinat in listLaporan!!) {
                    if(koordinat.size > 1){
                        val location = LatLng(koordinat[1], koordinat[0])
                        it.addMarker(MarkerOptions().position(location))
                    }
                }
            }
        })
    }

    private fun testToken(){
        val token = sharedPreferences.getString(getString(R.string.key_token), null).toString()
        val refreshToken = sharedPreferences.getString(getString(R.string.key_refresh_token), null).toString()
        if(token == null){
            startActivity(Intent(this, LoginActivity::class.java))
        }else{
            GlobalScope.async(Dispatchers.Main) {
                val test = GlobalScope.async {
                    if(userViewModel.testToken(token) != 200){
                        userViewModel.reAuth(refreshToken)?.let{
                            with(sharedPreferences.edit()){
                                putString(getString(R.string.key_token), it.token)
                                putString(getString(R.string.key_refresh_token), it.refreshToken)
                                commit()
                            }
                        } ?: startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                    }
                }
                println(test.await())
                checkRole(token)
            }

        }
    }

    private fun checkRole(token : String){
        role = JWT(token.replace("Bearer ", "")).getClaim("role").asString()
        if(role.toString() != "user"){
            startActivity(Intent(this@HomeActivity, AdminHomeActivity::class.java))
        }
    }
}