package com.example.recipeapi

import android.graphics.fonts.FontStyle
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.example.recipeapi.ui.theme.RecipeApiTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.http.Path

data class RecipeResponse(
    val results: List<Recipe>,
    val offset: Int,
    val number: Int,
    val totalResults: Int
)

data class Recipe(
    val id: Int,
    val title: String,
    val image: String,
    val imageType: String
)

data class RecipeDetails(
    val id: Int,
    val title: String,
    val image: String,
    val servings: Int,
    val readyInMinutes: Int,
    val instructions: String?,
    val extendedIngredients: List<Ingredient>
)

data class Ingredient(
    val id: Int,
    @Json(name = "original") val original: String
)

interface SpoonacularApi {
    @GET("recipes/complexSearch")
    suspend fun searchRecipes(
        @Query("query") query: String?,
        @Query("cuisine") cuisine: String?,
        @Query("diet") diet: String?,
        @Query("maxCalories") maxCalories: Int?,
        @Query("number") number: Int = 10,
        @Query("apiKey") apiKey: String
    ): RecipeResponse

    @GET("recipes/{id}/information")
    suspend fun getRecipeDetails(
        @Path("id") id: Int,
        @Query("includeNutrition") includeNutrition: Boolean = false,
        @Query("apiKey") apiKey: String
    ): RecipeDetails
}

object RetrofitInstance {
    private const val BASE_URL = "https://api.spoonacular.com/"
    private const val API_KEY = "5793693ec5114036a3e07450f17cc63f"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: SpoonacularApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpoonacularApi::class.java)
    }

    fun getApiKey(): String = API_KEY
}

class RecipeRepository {
    private val apiKey = RetrofitInstance.getApiKey()

    fun searchRecipes(
        query: String?,
        cuisine: String?,
        diet: String?,
        maxCalories: Int?
    ): Flow<List<Recipe>> = flow {
        val response = RetrofitInstance.api.searchRecipes(query, cuisine, diet, maxCalories, apiKey = apiKey)
        emit(response.results)
    }.catch { e ->
        Log.e("RecipeRepository", "Error fetching recipes", e)
        emit(emptyList())
    }

    fun getRecipeDetails(id: Int): Flow<RecipeDetails?> = flow {
        val details = RetrofitInstance.api.getRecipeDetails(id, apiKey = apiKey)
        emit(details)
    }.catch { e ->
        Log.e("RecipeRepository", "Error fetching recipe details", e)
    }
}

class RecipeViewModel : ViewModel() {
    private val repository = RecipeRepository()

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _recipeDetails = MutableStateFlow<RecipeDetails?>(null)
    val recipeDetails: StateFlow<RecipeDetails?> = _recipeDetails

    fun searchRecipes(
        query: String?,
        cuisine: String?,
        diet: String?,
        maxCalories: Int?
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.searchRecipes(query, cuisine, diet, maxCalories)
                .collect { recipes ->
                    _recipes.value = recipes
                    _isLoading.value = false
                }
        }
    }

    fun getRecipeDetails(id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getRecipeDetails(id)
                .collect { details ->
                    _recipeDetails.value = details
                    _isLoading.value = false
                }
        }
    }
}



@Composable
fun AppContent(viewModel: RecipeViewModel) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "search") {
        composable("search") {
            SearchScreen(navController, viewModel)
        }
        composable(
            "detail/{recipeId}",
            arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getInt("recipeId") ?: return@composable
            viewModel.getRecipeDetails(recipeId)
            DetailScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, viewModel: RecipeViewModel) {
    val recipes by viewModel.recipes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    var cuisine by remember { mutableStateOf("") }
    var diet by remember { mutableStateOf("") }
    var maxCalories by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe Finder") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Search Fields
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Recipe Name or Ingredients") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = cuisine,
                onValueChange = { cuisine = it },
                label = { Text("Cuisine Type (e.g., Italian)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = diet,
                onValueChange = { diet = it },
                label = { Text("Diet (e.g., Vegetarian)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = maxCalories,
                onValueChange = { maxCalories = it },
                label = { Text("Max Calories") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.searchRecipes(
                        query.ifBlank { null },
                        cuisine.ifBlank { null },
                        diet.ifBlank { null },
                        maxCalories.toIntOrNull()
                    )
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Search")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {

                if (recipes.isNotEmpty()) {
                    Text(
                        text = "Results:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        RecipeList(recipes = recipes, onRecipeClick = { recipe ->
                            navController.navigate("detail/${recipe.id}")
                        })
                    }
                }
            }
        }
    }
}


@Composable
fun RecipeList(recipes: List<Recipe>, onRecipeClick: (Recipe) -> Unit) {
    LazyColumn {
        items(recipes) { recipe ->
            RecipeItem(recipe = recipe, onClick = { onRecipeClick(recipe) })
        }
    }
}

@Composable
fun RecipeItem(recipe: Recipe, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = recipe.image,
            contentDescription = recipe.title,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: RecipeViewModel) {
    val recipeDetails by viewModel.recipeDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipeDetails?.title ?: "Recipe Details") }
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            recipeDetails != null -> {

                val details = recipeDetails!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    AsyncImage(
                        model = details.image,
                        contentDescription = details.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))


                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))


                    Text(text = "Servings: ${details.servings}")
                    Text(text = "Ready in ${details.readyInMinutes} minutes")
                    Spacer(modifier = Modifier.height(8.dp))


                    Text(
                        text = "Ingredients:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    for (ingredient in details.extendedIngredients) {
                        Text("- ${ingredient.original}")
                    }
                    Spacer(modifier = Modifier.height(16.dp))


                    if (!details.instructions.isNullOrEmpty()) {
                        Text(
                            text = "Instructions:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = details.instructions!!,
                            textAlign = TextAlign.Justify
                        )
                    } else {
                        Text(
                            text = "No instructions available.",
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.Gray
                        )
                    }
                }
            }
            else -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Failed to load recipe details.")
                }
            }
        }
    }
}






class MainActivity : ComponentActivity() {
    private val viewModel: RecipeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecipeApiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppContent(viewModel)
                }
            }
        }
    }
}



