package com.school.attendance

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- Models ---
data class StudentItem(
    @SerializedName("rollNo") val rollNo: Any,
    @SerializedName("name") val name: String,
    @SerializedName("grade") val grade: String,
    @SerializedName("section") val section: String
)

data class AttendanceRecordItem(
    val rollNo: Any,
    val name: String,
    val status: String
)

data class AttendancePayload(
    val date: String,
    val grade: String,
    val section: String,
    val records: List<AttendanceRecordItem>
)

data class ServerResponse(
    val status: String,
    val count: Int? = null
)

// --- API Service ---
interface SchoolApiService {
    @GET("exec")
    suspend fun getStudents(
        @Query("action") action: String = "getStudents",
        @Query("grade") grade: String,
        @Query("section") section: String
    ): List<StudentItem>

    @POST("exec")
    suspend fun submitAttendance(@Body payload: AttendancePayload): Response<ServerResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://script.google.com/macros/s/AKfycbyy3qeD2mOaWPG0qtIjTjZwoMwHm1QSbyNTV4RNKGdOVsSOmewWPJjclFeRtpvST3ZnSw/"

    val api: SchoolApiService by lazy {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SchoolApiService::class.java)
    }
}

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AttendanceScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val grades = remember { listOf("Nursery", "LKG", "UKG") + (1..12).map { "Class $it" } }
    val sections = remember { listOf("A", "B", "C") }

    var selectedGrade by remember { mutableStateOf("Nursery") }
    var selectedSection by remember { mutableStateOf("A") }
    var students by remember { mutableStateOf<List<StudentItem>>(emptyList()) }
    var attendanceMap by remember { mutableStateOf>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadStudents() {
        isLoading = true
        coroutineScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getStudents(grade = selectedGrade, section = selectedSection)
                }
                students = list
                attendanceMap = list.associate { it.rollNo.toString() to "Present" }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedGrade, selectedSection) {
        loadStudents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School Attendance") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (students.isEmpty()) return@Button
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val records = students.map {
                                AttendanceRecordItem(
                                    rollNo = it.rollNo,
                                    name = it.name,
                                    status = attendanceMap[it.rollNo.toString()] ?: "Present"
                                )
                            }
                            val payload = AttendancePayload(today, selectedGrade, selectedSection, records)
                            val res = withContext(Dispatchers.IO) { RetrofitClient.api.submitAttendance(payload) }
                            if (res.isSuccessful) {
                                Toast.makeText(context, "Drive Sheet me attendance save ho gayi!", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isLoading) "Processing..." else "Drive me Save Karein")
            }
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Class: $selectedGrade", style = MaterialTheme.typography.titleMedium)
                Text("Sec: $selectedSection", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(students) { stu ->
                        val rollKey = stu.rollNo.toString()
                        val currentStatus = attendanceMap[rollKey] ?: "Present"

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${stu.rollNo}: ${stu.name}", style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BadgeButton("P", Color(0xFF2E7D32), currentStatus == "Present") {
                                        attendanceMap = attendanceMap + (rollKey to "Present")
                                    }
                                    BadgeButton("A", Color(0xFFC62828), currentStatus == "Absent") {
                                        attendanceMap = attendanceMap + (rollKey to "Absent")
                                    }
                                    BadgeButton("L", Color(0xFFEF6C00), currentStatus == "Late") {
                                        attendanceMap = attendanceMap + (rollKey to "Late")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeButton(text: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(if (isSelected) color else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (isSelected) Color.White else Color.Black)
    }
}
