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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
    val sections = remember { listOf("A", "B", "C", "D") }

    var selectedGrade by remember { mutableStateOf("Nursery") }
    var selectedSection by remember { mutableStateOf("A") }
    
    var gradeMenuExpanded by remember { mutableStateOf(false) }
    var sectionMenuExpanded by remember { mutableStateOf(false) }

    var students by remember { mutableStateOf(emptyList<StudentItem>()) }
    var attendanceMap by remember { mutableStateOf(emptyMap<String, String>()) }
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
                actions = {
                    IconButton(onClick = { loadStudents() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (students.isEmpty()) {
                        Toast.makeText(context, "Is class me koi student nahi hai!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
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
                                Toast.makeText(context, "Drive Sheet me save ho gayi!", Toast.LENGTH_LONG).show()
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
                Text(if (isLoading) "Processing..." else "Drive me Save Karein (${students.size} Students)")
            }
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            // Interactive Dropdown Selectors
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Class Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = gradeMenuExpanded,
                    onExpandedChange = { gradeMenuExpanded = !gradeMenuExpanded },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = selectedGrade,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = gradeMenuExpanded,
                        onDismissRequest = { gradeMenuExpanded = false }
                    ) {
                        grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade) },
                                onClick = {
                                    selectedGrade = grade
                                    gradeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Section Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = sectionMenuExpanded,
                    onExpandedChange = { sectionMenuExpanded = !sectionMenuExpanded },
                    modifier = Modifier.weight(0.8f)
                ) {
                    OutlinedTextField(
                        value = selectedSection,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sec") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sectionMenuExpanded,
                        onDismissRequest = { sectionMenuExpanded = false }
                    ) {
                        sections.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text(sec) },
                                onClick = {
                                    selectedSection = sec
                                    sectionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Is Class ($selectedGrade - $selectedSection) me koi student nahi mila.\nSheet me students add karein.", color = Color.Gray, fontSize = 14.sp)
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stu.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Roll: ${stu.rollNo}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
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
