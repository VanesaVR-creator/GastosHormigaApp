package com.example.gastoshormiga

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================
// PALETA DE COLORES DE LA APLICACIÓN
// Define los colores principales usados en pantallas, botones,
// tarjetas, gráficas, textos y fondos para mantener una identidad visual uniforme.
// ============================================================

val Azul = Color(0xFF2E3DB7)
val AzulOscuro = Color(0xFF16217F)
val Verde = Color(0xFF86F47A)
val VerdeFuerte = Color(0xFF0A7F2E)
val Amarillo = Color(0xFFFFC107)
val Rojo = Color(0xFFD32F2F)
val Fondo = Color(0xFFFAF8FF)
val Campo = Color(0xFFF0EEF8)
val Texto = Color(0xFF202124)
val TextoSec = Color(0xFF5F6368)

// ============================================================
// MODELOS DE DATOS Y BASE DE DATOS LOCAL
// Estas clases representan la información que se guarda en Room:
// usuarios registrados, gastos capturados y operaciones disponibles
// para consultar, insertar, actualizar o eliminar registros.
// ============================================================

@Entity(indices = [Index(value = ["email"], unique = true)])
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val email: String,
    val passwordHash: String,
    val presupuestoMensual: Double,
    val alerta80: Boolean = true
)

@Entity(indices = [Index(value = ["idUsuario"]), Index(value = ["fechaMillis"]), Index(value = ["categoria"])])
data class Gasto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idUsuario: Int,
    val categoria: String,
    val monto: Double,
    val descripcion: String,
    val fechaMillis: Long,
    val metodoPago: String
)

@Dao
interface GastosDao {
    @Query("SELECT * FROM Usuario WHERE email = :email LIMIT 1")
    suspend fun buscarUsuario(email: String): Usuario?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun registrarUsuario(usuario: Usuario): Long

    @Update
    suspend fun actualizarUsuario(usuario: Usuario)

    @Delete
    suspend fun eliminarUsuario(usuario: Usuario)

    @Query("DELETE FROM Gasto WHERE idUsuario = :idUsuario")
    suspend fun eliminarGastosUsuario(idUsuario: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarGasto(gasto: Gasto): Long

    @Delete
    suspend fun eliminarGasto(gasto: Gasto)

    @Query("SELECT * FROM Gasto WHERE idUsuario = :idUsuario ORDER BY fechaMillis DESC")
    suspend fun listarGastos(idUsuario: Int): List<Gasto>
}

@Database(entities = [Usuario::class, Gasto::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GastosDao
}

// ============================================================
// CATEGORÍAS DE GASTOS
// Define las categorías disponibles para clasificar los gastos,
// incluyendo su nombre, texto corto, ícono visual y color asociado.
// ============================================================

data class Categoria(val nombre: String, val corto: String, val icono: String, val color: Color)

val categorias = listOf(
    Categoria("Alimentación", "Comida", "🍴", Azul),
    Categoria("Transporte", "Viajes", "🚌", VerdeFuerte),
    Categoria("Entretenimiento", "Ocio", "🎬", Amarillo),
    Categoria("Educación", "Uni", "📘", Azul),
    Categoria("Salud", "Salud", "➕", Rojo),
    Categoria("Suscripciones", "Apps", "▶", Azul),
    Categoria("Ropa y Accesorios", "Ropa", "👕", Azul),
    Categoria("Otros", "Otros", "•••", TextoSec)
)

// ============================================================
// ACTIVIDAD PRINCIPAL
// Inicializa la base de datos local, configura el tema visual
// y carga la interfaz principal creada con Jetpack Compose.
// ============================================================

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "gastoshormiga-db")
            .fallbackToDestructiveMigration()
            .build()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Azul, secondary = Verde, background = Fondo)) {
                GastosHormigaApp(db)
            }
        }
    }
}

// ============================================================
// CONTROL DE NAVEGACIÓN
// Enumera las pantallas principales de la aplicación para manejar
// el flujo entre login, registro, inicio, historial, reportes y perfil.
// ============================================================

enum class Pantalla { LOGIN, REGISTRO, INICIO, NUEVO_GASTO, HISTORIAL, REPORTES, PERFIL }

// ============================================================
// CONTENEDOR PRINCIPAL DE LA APP
// Administra el estado global de la sesión, la pantalla actual,
// el usuario autenticado y la lista de gastos cargada desde Room.
// ============================================================

@Composable
fun GastosHormigaApp(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var pantalla by remember { mutableStateOf(Pantalla.LOGIN) }
    var usuario by remember { mutableStateOf<Usuario?>(null) }
    var gastos by remember { mutableStateOf<List<Gasto>>(emptyList()) }

    fun cargarGastos(u: Usuario) {
        scope.launch { gastos = db.dao().listarGastos(u.id) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Fondo) {
        when (pantalla) {
            Pantalla.LOGIN -> LoginScreen(
                onLogin = { email, pass, ctx ->
                    scope.launch {
                        val u = db.dao().buscarUsuario(email.trim().lowercase())
                        if (u != null && u.passwordHash == sha256(pass)) {
                            usuario = u
                            gastos = db.dao().listarGastos(u.id)
                            pantalla = Pantalla.INICIO
                        } else {
                            Toast.makeText(ctx, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRegistro = { pantalla = Pantalla.REGISTRO }
            )
            Pantalla.REGISTRO -> RegistroScreen(
                onBack = { pantalla = Pantalla.LOGIN },
                onCrear = { nombre, email, pass, presupuesto, ctx ->
                    scope.launch {
                        try {
                            val id = db.dao().registrarUsuario(
                                Usuario(
                                    nombre = nombre.trim(),
                                    email = email.trim().lowercase(),
                                    passwordHash = sha256(pass),
                                    presupuestoMensual = presupuesto
                                )
                            ).toInt()
                            val u = Usuario(id, nombre.trim(), email.trim().lowercase(), sha256(pass), presupuesto)
                            usuario = u
                            gastos = emptyList()
                            pantalla = Pantalla.INICIO
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "No se pudo crear la cuenta. Revisa si el correo ya existe.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            Pantalla.INICIO -> AppShell(pantalla, { pantalla = it }) {
                DashboardScreen(
                    usuario = usuario!!,
                    gastos = gastos,
                    onNuevoGasto = { pantalla = Pantalla.NUEVO_GASTO },
                    onVerTodo = { pantalla = Pantalla.HISTORIAL }
                )
            }
            Pantalla.NUEVO_GASTO -> NuevoGastoScreen(
                onClose = { pantalla = Pantalla.INICIO },
                onGuardar = { gasto, ctx ->
                    scope.launch {
                        db.dao().guardarGasto(gasto.copy(idUsuario = usuario!!.id))
                        cargarGastos(usuario!!)
                        Toast.makeText(ctx, "Gasto guardado", Toast.LENGTH_SHORT).show()
                        pantalla = Pantalla.INICIO
                    }
                }
            )
            Pantalla.HISTORIAL -> AppShell(pantalla, { pantalla = it }) {
                HistorialScreen(gastos = gastos, onNuevo = { pantalla = Pantalla.NUEVO_GASTO }, onEliminar = { g ->
                    scope.launch {
                        db.dao().eliminarGasto(g)
                        cargarGastos(usuario!!)
                    }
                })
            }
            Pantalla.REPORTES -> AppShell(pantalla, { pantalla = it }) {
                ReportesScreen(gastos = gastos)
            }
            Pantalla.PERFIL -> AppShell(pantalla, { pantalla = it }) {
                PerfilScreen(usuario = usuario!!, gastos = gastos,
                    onActualizar = { nuevoUsuario ->
                        scope.launch {
                            db.dao().actualizarUsuario(nuevoUsuario)
                            usuario = nuevoUsuario
                        }
                    },
                    onCerrarSesion = {
                        usuario = null
                        gastos = emptyList()
                        pantalla = Pantalla.LOGIN
                    },
                    onEliminarCuenta = { ctx ->
                        scope.launch {
                            db.dao().eliminarGastosUsuario(usuario!!.id)
                            db.dao().eliminarUsuario(usuario!!)
                            usuario = null
                            gastos = emptyList()
                            pantalla = Pantalla.LOGIN
                            Toast.makeText(ctx, "Cuenta eliminada", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

// ============================================================
// PANTALLAS DE AUTENTICACIÓN
// Contienen el inicio de sesión y el registro de usuarios,
// validando los datos capturados antes de acceder a la aplicación.
// ============================================================

@Composable
fun LoginScreen(onLogin: (String, String, Context) -> Unit, onRegistro: () -> Unit) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 74.dp, bottom = 40.dp)
    ) {
        item {
            LogoHormiga(150)
            Spacer(Modifier.height(28.dp))
            Text("Controla tus gastos hormiga", color = Texto, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Registra pequeños gastos y descubre en qué se va tu dinero", color = TextoSec, fontSize = 21.sp, textAlign = TextAlign.Center, lineHeight = 30.sp)
            Spacer(Modifier.height(36.dp))
            CardRedonda {
                Label("Correo electrónico")
                AppTextField(value = email, onValueChange = { email = it }, placeholder = "ejemplo@universidad.edu.mx", leading = "✉", keyboard = KeyboardType.Email)
                Spacer(Modifier.height(18.dp))
                Label("Contraseña")
                AppTextField(value = pass, onValueChange = { pass = it }, placeholder = "••••••••", leading = "🔒", trailing = if (visible) "🙈" else "👁", onTrailing = { visible = !visible }, visual = if (visible) VisualTransformation.None else PasswordVisualTransformation())
                Text("¿Olvidaste tu contraseña?", color = AzulOscuro, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.End, fontSize = 16.sp)
                Spacer(Modifier.height(22.dp))
                ButtonPrimary("Iniciar sesión  →") {
                    if (!email.contains("@") || pass.isBlank()) Toast.makeText(ctx, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show() else onLogin(email, pass, ctx)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f)); Text("  o continúa con  ", color = Color(0xFFB8B8C9)); Divider(Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("¿No tienes cuenta? ", color = TextoSec, fontSize = 17.sp)
                    Text("Regístrate", color = Azul, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.clickable { onRegistro() })
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("🛡  Seguro     ⚡  Rápido", color = TextoSec, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RegistroScreen(onBack: () -> Unit, onCrear: (String, String, String, Double, Context) -> Unit) {
    val ctx = LocalContext.current
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var presupuesto by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(22.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp)) {
        item {
            HeaderSimple()
            Spacer(Modifier.height(32.dp))
            Text("Crea tu cuenta", fontSize = 31.sp, color = Texto, fontWeight = FontWeight.Normal)
            Text("Únete a la comunidad de ahorro universitario.", fontSize = 18.sp, color = TextoSec, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
            CardRedonda {
                Label("Nombre completo")
                AppTextField(nombre, { nombre = it }, "Ej. Juan Pérez", "👤")
                Spacer(Modifier.height(16.dp))
                Label("Correo electrónico")
                AppTextField(email, { email = it }, "usuario@universidad.mx", "✉", keyboard = KeyboardType.Email)
                Spacer(Modifier.height(16.dp))
                Label("Contraseña")
                AppTextField(pass, { pass = it }, "••••••••", "🔒", trailing = if (visible) "🙈" else "👁", onTrailing = { visible = !visible }, visual = if (visible) VisualTransformation.None else PasswordVisualTransformation())
                Spacer(Modifier.height(16.dp))
                Label("Presupuesto Mensual (MXN)")
                AppTextField(presupuesto, { presupuesto = it }, "$ 0.00", "💵", keyboard = KeyboardType.Number)
                Text("Este será tu límite mensual sugerido.", color = Color(0xFF7A6312), modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp).clip(RoundedCornerShape(14.dp)).background(Campo).padding(18.dp), contentAlignment = Alignment.Center) {
                    Text("🛡 Tus datos se guardan de forma local para tu privacidad.", color = TextoSec, textAlign = TextAlign.Center, fontSize = 16.sp)
                }
                ButtonPrimary("Crear cuenta  →") {
                    val p = presupuesto.toDoubleOrNull() ?: 0.0
                    when {
                        nombre.trim().length < 3 -> Toast.makeText(ctx, "Ingresa tu nombre completo", Toast.LENGTH_SHORT).show()
                        !email.contains("@") -> Toast.makeText(ctx, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
                        pass.length < 8 -> Toast.makeText(ctx, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show()
                        p <= 0.0 -> Toast.makeText(ctx, "Ingresa un presupuesto mayor a 0", Toast.LENGTH_SHORT).show()
                        else -> onCrear(nombre, email, pass, p, ctx)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("¿Ya tienes cuenta? ", color = TextoSec, fontSize = 16.sp)
                Text("Inicia sesión aquí", color = Azul, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.clickable { onBack() })
            }
        }
    }
}

// ============================================================
// ESTRUCTURA GENERAL Y MENÚ INFERIOR
// Envuelve las pantallas internas de la aplicación y muestra
// la barra de navegación para cambiar entre secciones principales.
// ============================================================

@Composable
fun AppShell(actual: Pantalla, navegar: (Pantalla) -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Fondo)) {
        content()
        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter), containerColor = Color(0xFFF0EEF8)) {
            NavItem("Inicio", "⌂", actual == Pantalla.INICIO) { navegar(Pantalla.INICIO) }
            NavItem("Historial", "↶", actual == Pantalla.HISTORIAL) { navegar(Pantalla.HISTORIAL) }
            NavItem("Reportes", "▣", actual == Pantalla.REPORTES) { navegar(Pantalla.REPORTES) }
            NavItem("Perfil", "♟", actual == Pantalla.PERFIL) { navegar(Pantalla.PERFIL) }
        }
    }
}

@Composable
fun RowScope.NavItem(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(icon, fontSize = 20.sp) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(indicatorColor = Verde, selectedIconColor = VerdeFuerte, selectedTextColor = VerdeFuerte)
    )
}

// ============================================================
// PANTALLA DE INICIO / DASHBOARD
// Muestra el resumen mensual del usuario, el avance del presupuesto,
// los últimos gastos registrados y accesos rápidos a nuevas acciones.
// ============================================================

@Composable
fun DashboardScreen(usuario: Usuario, gastos: List<Gasto>, onNuevoGasto: () -> Unit, onVerTodo: () -> Unit) {
    val totalMes = gastosMesActual(gastos).sumOf { it.monto }
    val porcentaje = if (usuario.presupuestoMensual > 0) (totalMes / usuario.presupuestoMensual).coerceAtMost(1.0) else 0.0
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 112.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("☂", color = Azul, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hola estudiante", fontSize = 27.sp, color = Texto)
                        Text("¡Cuida tus hormiguitas!", color = TextoSec, fontSize = 15.sp)
                    }
                    Avatar()
                }
                Spacer(Modifier.height(26.dp))
                CardRedonda {
                    Text("Total gastado este mes", color = TextoSec, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(moneda(totalMes), color = AzulOscuro, fontFamily = FontFamily.Monospace, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("  MXN", color = TextoSec, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Presupuesto: ${moneda(usuario.presupuestoMensual)}", color = TextoSec, modifier = Modifier.weight(1f))
                        Text("⚠ ${(porcentaje * 100).roundToInt()}%", color = Texto, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (porcentaje >= .8) Amarillo else Campo).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    LinearProgressIndicator(progress = { porcentaje.toFloat() }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(10.dp)), color = if (porcentaje >= .8) Amarillo else VerdeFuerte, trackColor = Campo)
                    if (porcentaje >= .8) Text("Estás cerca del límite (80%) en algunas categorías.", color = TextoSec, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(22.dp))
                CardRedonda {
                    Text("Gastos por Categoría", color = Texto, fontSize = 18.sp)
                    DonutChart(gastos)
                    Leyenda(gastos)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    MiniCard("⌁", "Ahorro proyectado", moneda((usuario.presupuestoMensual - totalMes).coerceAtLeast(0.0)), VerdeFuerte, Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Azul).padding(20.dp)) {
                        Text("💡\n\nEvita el café diario\npara ahorrar $400\nextras.", color = Color.White, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Últimos gastos", fontSize = 24.sp, color = Texto, modifier = Modifier.weight(1f))
                    Text("Ver todos", color = Azul, modifier = Modifier.clickable { onVerTodo() })
                }
                Spacer(Modifier.height(8.dp))
            }
            items(gastos.take(5)) { g -> GastoItem(g) }
        }
        FloatingActionButton(onClick = onNuevoGasto, containerColor = Azul, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 96.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Agregar")
        }
    }
}

// ============================================================
// REGISTRO DE NUEVOS GASTOS
// Permite capturar monto, categoría, descripción, fecha, hora y método
// de pago antes de guardar el gasto en la base de datos local.
// ============================================================

@Composable
fun NuevoGastoScreen(onClose: () -> Unit, onGuardar: (Gasto, Context) -> Unit) {
    val ctx = LocalContext.current
    var monto by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf<Categoria?>(null) }
    var descripcion by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("Efectivo") }
    val ahora = remember { System.currentTimeMillis() }

    LazyColumn(Modifier.fillMaxSize().padding(22.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", modifier = Modifier.clickable { onClose() })
                Text("Nuevo Gasto", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp).weight(1f))
                Avatar()
            }
            Spacer(Modifier.height(44.dp))
            Text("Monto del gasto", textAlign = TextAlign.Center, color = TextoSec, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("$", color = Azul, fontSize = 28.sp)
                TextField(value = monto, onValueChange = { monto = it }, placeholder = { Text("0.00", fontSize = 48.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 48.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = TextFieldDefaults.colors(focusedContainerColor = Fondo, unfocusedContainerColor = Fondo, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.width(190.dp))
                Text("MXN", color = TextoSec, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Label("Categoría")
            Spacer(Modifier.height(12.dp))
            categorias.chunked(4).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    fila.forEach { c -> CategoriaButton(c, categoria == c) { categoria = c } }
                }
                Spacer(Modifier.height(16.dp))
            }
            Label("Descripción (Opcional)")
            AppTextField(descripcion, { descripcion = it }, "¿En qué gastaste?", "☰")
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Label("Fecha")
                    BoxInput("▪  ${fechaCorta(ahora)}     📅")
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Label("Hora")
                    BoxInput("◷  ${horaCorta(ahora)}")
                }
            }
            Spacer(Modifier.height(18.dp))
            Label("Método de pago")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Campo).padding(4.dp)) {
                listOf("Efectivo", "Tarjeta", "Transf.").forEach { m ->
                    Text(m, color = if (metodo == m) Texto else TextoSec, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (metodo == m) Verde else Color.Transparent).clickable { metodo = m }.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Azul).padding(24.dp)) {
                Text("Consejo del día\nLos gastos pequeños son hormigas que devoran tu ahorro.", color = Color.White, fontSize = 16.sp, lineHeight = 25.sp)
            }
            Spacer(Modifier.height(36.dp))
            ButtonPrimary("✓  Guardar gasto") {
                val valor = monto.toDoubleOrNull()
                when {
                    valor == null || valor <= 0 -> Toast.makeText(ctx, "El monto debe ser mayor a $0", Toast.LENGTH_SHORT).show()
                    valor >= 100000 -> Toast.makeText(ctx, "El monto excede el límite permitido", Toast.LENGTH_SHORT).show()
                    categoria == null -> Toast.makeText(ctx, "Selecciona una categoría", Toast.LENGTH_SHORT).show()
                    descripcion.length > 200 -> Toast.makeText(ctx, "La descripción no puede superar 200 caracteres", Toast.LENGTH_SHORT).show()
                    else -> onGuardar(Gasto(idUsuario = 0, categoria = categoria!!.nombre, monto = valor, descripcion = descripcion.ifBlank { categoria!!.nombre }, fechaMillis = System.currentTimeMillis(), metodoPago = metodo), ctx)
                }
            }
        }
    }
}

// ============================================================
// HISTORIAL DE GASTOS
// Presenta los gastos guardados, permite buscarlos por texto o monto,
// agruparlos por fecha y eliminar registros cuando sea necesario.
// ============================================================

@Composable
fun HistorialScreen(gastos: List<Gasto>, onNuevo: () -> Unit, onEliminar: (Gasto) -> Unit) {
    var busqueda by remember { mutableStateOf("") }
    val filtrados = gastos.filter { it.descripcion.contains(busqueda, true) || it.categoria.contains(busqueda, true) || it.monto.toString().contains(busqueda) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 26.dp, bottom = 112.dp)) {
            item {
                HeaderSimple()
                Spacer(Modifier.height(26.dp))
                TextField(value = busqueda, onValueChange = { busqueda = it }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, placeholder = { Text("Buscar gastos...") }, shape = RoundedCornerShape(16.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Campo, unfocusedContainerColor = Campo, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(true, { }, label = { Text("Categoría") }, leadingIcon = { Text("▵") })
                    FilterChip(false, { }, label = { Text("Método") }, leadingIcon = { Text("▭") })
                    FilterChip(false, { }, label = { Text("Fecha") }, leadingIcon = { Text("▣") })
                }
                Spacer(Modifier.height(24.dp))
            }
            val grupos = filtrados.groupBy { etiquetaDia(it.fechaMillis) }
            grupos.forEach { (dia, lista) ->
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dia, color = Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Total: ${moneda(lista.sumOf { it.monto })}", color = AzulOscuro, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE3E3FF)).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                items(lista) { g -> GastoItem(g, positivo = true, onEliminar = { onEliminar(g) }) }
            }
        }
        FloatingActionButton(onClick = onNuevo, containerColor = Azul, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 96.dp)) { Icon(Icons.Default.Add, contentDescription = null) }
    }
}

// ============================================================
// REPORTES Y ANÁLISIS DE GASTOS
// Muestra indicadores, resúmenes por categoría, actividad semanal
// y permite exportar la información registrada a un archivo CSV.
// ============================================================

@Composable
fun ReportesScreen(gastos: List<Gasto>) {
    val ctx = LocalContext.current
    var periodo by remember { mutableStateOf("Semana") }
    val total = gastos.sumOf { it.monto }
    val promedio = if (gastos.isEmpty()) 0.0 else total / 7.0
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 26.dp, bottom = 112.dp)) {
        item {
            HeaderSimple()
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Campo).padding(4.dp)) {
                listOf("Semana", "Mes", "3 Meses", "Personalizado").forEach { p ->
                    Text(p, color = if (periodo == p) Color.White else TextoSec, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (periodo == p) Azul else Color.Transparent).clickable { periodo = p }.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Azul).padding(20.dp)) {
                Column {
                    Text("Gasto promedio diario", color = Color.White.copy(.75f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${moneda(promedio)} MXN", color = Color.White, fontSize = 29.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("↘ -5% vs. semana pasada", color = Color.White.copy(.75f), fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            CardRedonda {
                Row(Modifier.fillMaxWidth()) {
                    Text("Actividad Semanal", fontSize = 20.sp, color = Texto, modifier = Modifier.weight(1f))
                    Text("Abr 10 - Abr 16", color = AzulOscuro, fontSize = 16.sp)
                }
                BarChart(gastos)
            }
            Spacer(Modifier.height(24.dp))
            Text("Gasto por Categoría", color = Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        val porCat = categorias.map { c -> c to gastos.filter { it.categoria == c.nombre }.sumOf { it.monto } }.filter { it.second > 0 }.ifEmpty { categorias.take(3).map { it to 0.0 } }
        items(porCat) { (cat, monto) -> CategoriaResumen(cat, monto, if (total > 0) monto / total else 0.0) }
        item {
            Spacer(Modifier.height(22.dp))
            ButtonPrimary("⇩  Exportar reporte      PDF   CSV") { exportarCsv(ctx, gastos) }
        }
    }
}

// ============================================================
// PERFIL Y CONFIGURACIÓN DEL USUARIO
// Permite visualizar datos de la cuenta, actualizar presupuesto,
// activar alertas, cerrar sesión o eliminar la cuenta junto con sus gastos.
// ============================================================

@Composable
fun PerfilScreen(usuario: Usuario, gastos: List<Gasto>, onActualizar: (Usuario) -> Unit, onCerrarSesion: () -> Unit, onEliminarCuenta: (Context) -> Unit) {
    val ctx = LocalContext.current
    var presupuesto by remember(usuario.presupuestoMensual) { mutableStateOf(usuario.presupuestoMensual.toString()) }
    var alerta by remember(usuario.alerta80) { mutableStateOf(usuario.alerta80) }
    val total = gastosMesActual(gastos).sumOf { it.monto }
    val progreso = if (usuario.presupuestoMensual > 0) (total / usuario.presupuestoMensual).coerceAtMost(1.0) else 0.0
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 26.dp, bottom = 112.dp)) {
        item {
            HeaderSimple()
            Spacer(Modifier.height(34.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) { Avatar(88); Box(Modifier.size(42.dp).clip(CircleShape).background(Azul), contentAlignment = Alignment.Center) { Text("✎", color = Color.White) } }
                Spacer(Modifier.height(12.dp))
                Text(usuario.nombre, color = Texto, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(usuario.email, color = TextoSec, fontSize = 18.sp)
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mis presupuestos", color = Azul, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Gestión de límites mensuales", color = TextoSec, fontSize = 16.sp)
                }
                OutlinedButton(onClick = {
                    val p = presupuesto.toDoubleOrNull() ?: usuario.presupuestoMensual
                    onActualizar(usuario.copy(presupuestoMensual = p, alerta80 = alerta))
                    Toast.makeText(ctx, "Presupuesto actualizado", Toast.LENGTH_SHORT).show()
                }) { Text("+ Nuevo") }
            }
            Spacer(Modifier.height(18.dp))
            CardRedonda {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("▣", color = Azul, fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text("Presupuesto Global", color = Texto, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Mes actual", color = TextoSec) }
                    Text(moneda(usuario.presupuestoMensual), color = AzulOscuro, fontFamily = FontFamily.Monospace, fontSize = 21.sp)
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { progreso.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)), color = Azul, trackColor = Campo)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text("${(progreso * 100).roundToInt()}% consumido", color = TextoSec, modifier = Modifier.weight(1f))
                    Text("Faltan ${moneda((usuario.presupuestoMensual - total).coerceAtLeast(0.0))}", color = TextoSec)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                BudgetSmall("🍴", "Comida", "${moneda(totalCategoria(gastos, "Alimentación"))}", VerdeFuerte, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                BudgetSmall("🚌", "Transporte", "${moneda(totalCategoria(gastos, "Transporte"))}", Amarillo, Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            Text("Configuración", color = TextoSec, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            CardRedonda(padding = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔", fontSize = 24.sp); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text("Alerta al 80% de gasto", color = Texto, fontSize = 17.sp); Text("Notificar antes del límite", color = TextoSec) }
                    Switch(checked = alerta, onCheckedChange = { alerta = it; onActualizar(usuario.copy(alerta80 = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VerdeFuerte))
                }
                Divider()
                RowConfig("🔒", "Privacidad y seguridad")
                Divider()
                RowConfig("⚙", "Preferencias de cuenta")
            }
            Spacer(Modifier.height(20.dp))
            Label("Editar presupuesto mensual")
            AppTextField(presupuesto, { presupuesto = it }, "${usuario.presupuestoMensual}", "💵", keyboard = KeyboardType.Number)
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cerrar sesión") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onEliminarCuenta(ctx) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Rojo), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("🗑  Eliminar cuenta") }
        }
    }
}

// ============================================================
// COMPONENTES VISUALES REUTILIZABLES
// Agrupa elementos de interfaz usados en varias pantallas, como encabezados,
// logo, avatar, tarjetas, campos de texto, botones y filas de configuración.
// ============================================================

@Composable
fun HeaderSimple() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("☂", color = Azul, fontSize = 26.sp)
        Text("GastosHormiga", color = Azul, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Avatar()
    }
}

@Composable
fun LogoHormiga(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((size * .70).dp)) {
            val w = this.size.width
            val h = this.size.height
            drawCircle(VerdeFuerte, radius = w * .23f, center = Offset(w * .58f, h * .24f))
            drawCircle(Azul, radius = w * .13f, center = Offset(w * .42f, h * .64f))
            drawCircle(Azul, radius = w * .18f, center = Offset(w * .62f, h * .60f))
            drawCircle(Azul, radius = w * .22f, center = Offset(w * .80f, h * .58f))
            drawLine(Azul, Offset(w * .40f, h * .78f), Offset(w * .28f, h * .95f), strokeWidth = 10f, cap = StrokeCap.Round)
            drawLine(Azul, Offset(w * .60f, h * .78f), Offset(w * .60f, h * .98f), strokeWidth = 10f, cap = StrokeCap.Round)
            drawLine(Azul, Offset(w * .76f, h * .76f), Offset(w * .90f, h * .95f), strokeWidth = 10f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(w * .50f, h * .23f), Offset(w * .67f, h * .23f), strokeWidth = 8f, cap = StrokeCap.Round)
        }
        Text("$", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * .18).sp, modifier = Modifier.offset(y = (-(size * .25)).dp))
    }
}

@Composable
fun Avatar(size: Int = 46) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE9F1FF)).border(2.dp, Azul, CircleShape), contentAlignment = Alignment.Center) {
        Text("👤", fontSize = (size * .45).sp)
    }
}

@Composable
fun CardRedonda(padding: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

@Composable
fun Label(text: String) { Text(text, color = AzulOscuro, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp)) }

@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, leading: String, trailing: String? = null, onTrailing: (() -> Unit)? = null, keyboard: KeyboardType = KeyboardType.Text, visual: VisualTransformation = VisualTransformation.None) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = { Text(leading, fontSize = 20.sp) },
        trailingIcon = if (trailing != null) ({ Text(trailing, modifier = Modifier.clickable { onTrailing?.invoke() }, fontSize = 20.sp) }) else null,
        placeholder = { Text(placeholder, color = Color(0xFFB5B4C5)) },
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = Campo, unfocusedContainerColor = Campo, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ButtonPrimary(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Azul, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(6.dp)) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun BoxInput(text: String) { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Campo).padding(14.dp)) { Text(text, color = Texto, fontSize = 15.sp) } }

@Composable
fun CategoriaButton(cat: Categoria, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp).clickable { onClick() }) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (selected) Verde else Campo), contentAlignment = Alignment.Center) { Text(cat.icono, fontSize = 24.sp, color = Azul) }
        Text(cat.corto, color = TextoSec, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

// ============================================================
// COMPONENTES DE GRÁFICAS Y RESÚMENES VISUALES
// Construye las gráficas de dona y barras, además de leyendas,
// tarjetas de resumen y progreso visual por categoría.
// ============================================================

@Composable
fun DonutChart(gastos: List<Gasto>) {
    val datos = categorias.map { c -> c to gastos.filter { it.categoria == c.nombre }.sumOf { it.monto } }.filter { it.second > 0 }
    val total = datos.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            var start = -90f
            if (datos.isEmpty()) {
                drawArc(Campo, 0f, 360f, false, style = Stroke(42f, cap = StrokeCap.Butt))
            } else {
                datos.forEach { (cat, monto) ->
                    val sweep = (monto / total * 360).toFloat()
                    drawArc(cat.color, start, sweep, false, style = Stroke(42f, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        Text("⊕", color = Azul, fontSize = 30.sp)
    }
}

@Composable
fun Leyenda(gastos: List<Gasto>) {
    val datos = categorias.map { c -> c to gastos.filter { it.categoria == c.nombre }.sumOf { it.monto } }.filter { it.second > 0 }.take(3)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        datos.ifEmpty { categorias.take(2).map { it to 0.0 } }.forEach { (cat, _) -> Text("● ${cat.corto}", color = cat.color, fontSize = 12.sp) }
    }
}

@Composable
fun MiniCard(icon: String, titulo: String, valor: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(20.dp)) { Text(icon, color = color, fontSize = 24.sp); Spacer(Modifier.height(16.dp)); Text(titulo, color = TextoSec); Text(valor, color = color, fontSize = 20.sp) }
    }
}

@Composable
fun GastoItem(g: Gasto, positivo: Boolean = false, onEliminar: (() -> Unit)? = null) {
    val cat = categorias.firstOrNull { it.nombre == g.categoria } ?: categorias.last()
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(cat.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Text(cat.icono, fontSize = 24.sp) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(g.descripcion, color = Texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${cat.corto} • ${etiquetaDia(g.fechaMillis)}", color = TextoSec, fontSize = 13.sp)
                Text("▰ ${g.metodoPago}", color = TextoSec, fontSize = 12.sp)
            }
            Text(if (positivo) moneda(g.monto) else "-${moneda(g.monto)}", color = if (positivo) Texto else Rojo, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            if (onEliminar != null) Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Rojo, modifier = Modifier.padding(start = 10.dp).clickable { onEliminar() })
        }
    }
}

@Composable
fun BarChart(gastos: List<Gasto>) {
    val dias = (0..6).map { idx -> gastos.filter { diaSemana(it.fechaMillis) == idx }.sumOf { it.monto } }
    val max = dias.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(Modifier.fillMaxWidth().height(230.dp).padding(top = 18.dp)) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val barWidth = size.width / 10f
            dias.forEachIndexed { i, value ->
                val h = (value / max * size.height).toFloat()
                val x = (i + 1) * size.width / 8f - barWidth / 2
                drawRoundRect(color = if (i % 2 == 0) Azul else VerdeFuerte, topLeft = Offset(x, size.height - h), size = Size(barWidth, h.coerceAtLeast(4f)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { listOf("LU", "MA", "MI", "JU", "VI", "SA", "DO").forEach { Text(it, color = TextoSec, fontSize = 11.sp) } }
    }
}

@Composable
fun CategoriaResumen(cat: Categoria, monto: Double, porcentaje: Double) {
    Card(Modifier.fillMaxWidth().padding(vertical = 7.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(cat.color.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text(cat.icono, fontSize = 23.sp) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(cat.nombre, color = Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { porcentaje.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(5.dp)), color = cat.color, trackColor = Campo)
            }
            Text(moneda(monto), color = AzulOscuro, fontFamily = FontFamily.Monospace)
            Text("  ${(porcentaje * 100).roundToInt()}%", color = Texto, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BudgetSmall(icon: String, titulo: String, valor: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(18.dp)) { Text(icon, fontSize = 24.sp); Text(titulo, color = Texto, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(valor, color = Texto, fontFamily = FontFamily.Monospace, fontSize = 17.sp); LinearProgressIndicator(progress = { .55f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(RoundedCornerShape(4.dp)), color = color, trackColor = Campo) }
    }
}

@Composable
fun RowConfig(icon: String, texto: String) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 23.sp); Text(texto, color = Texto, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 16.dp)); Text("›", color = TextoSec, fontSize = 28.sp) } }

// ============================================================
// FUNCIONES AUXILIARES
// Incluye utilidades para cifrar contraseñas, formatear dinero y fechas,
// calcular totales mensuales, clasificar días y exportar reportes CSV.
// ============================================================

fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
fun moneda(v: Double): String = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(v).replace("MX$", "$")
fun fechaCorta(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX")).format(Date(millis))
fun horaCorta(millis: Long): String = SimpleDateFormat("hh:mm a", Locale("es", "MX")).format(Date(millis))
fun mesActualKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
fun gastosMesActual(gastos: List<Gasto>): List<Gasto> = gastos.filter { SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.fechaMillis)) == mesActualKey() }
fun totalCategoria(gastos: List<Gasto>, cat: String) = gastosMesActual(gastos).filter { it.categoria == cat }.sumOf { it.monto }
fun etiquetaDia(millis: Long): String {
    val hoy = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val ayer = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(System.currentTimeMillis() - 86400000))
    val d = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))
    return when (d) { hoy -> "Hoy"; ayer -> "Ayer"; else -> SimpleDateFormat("dd MMM", Locale("es", "MX")).format(Date(millis)) }
}
fun diaSemana(millis: Long): Int {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val d = cal.get(java.util.Calendar.DAY_OF_WEEK)
    return if (d == java.util.Calendar.SUNDAY) 6 else d - 2
}
fun exportarCsv(ctx: Context, gastos: List<Gasto>) {
    val csv = buildString {
        appendLine("id,fecha,categoria,monto,descripcion,metodo_pago")
        gastos.forEach { appendLine("${it.id},${fechaCorta(it.fechaMillis)},${it.categoria},${it.monto},${it.descripcion},${it.metodoPago}") }
    }
    val file = File(ctx.filesDir, "reporte_gastoshormiga.csv")
    file.writeText(csv)
    Toast.makeText(ctx, "Reporte CSV creado: ${file.absolutePath}", Toast.LENGTH_LONG).show()
}
