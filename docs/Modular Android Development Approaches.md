# **Modularized Android Development: Architectural Isolation and Scalable Systems**

## **1\. The Architectural Imperative: From Monolith to Micro-Apps**

The evolution of Android application development has traversed a significant arc from simple, single-module projects to complex, distributed systems. As mobile applications have matured into critical business platforms, the traditional monolithic architecture—where all code resides within a single app module—has proven insufficient for scaling engineering teams and maintaining code velocity. The modern architectural paradigm shifts towards **Modularization**, specifically a "Micro-App" or "Harness-Driven" approach, where individual features are developed, executed, and tested in isolation before being combined into a cohesive production artifact.1

This report provides an exhaustive analysis of the strategies, build configurations, and dependency management patterns required to implement a modular Android architecture that supports running individual pieces "all alone." This capability is not merely a convenience; it is a structural necessity for reducing build times, enforcing separation of concerns, and enabling parallel development across large engineering organizations.3

### **1.1 The Decomposition of the Monolith**

In a monolithic codebase, the tight coupling between the User Interface (UI), Domain logic, and Data layers creates a rigid structure where changes in one area ripple unpredictably through the system. For instance, modifying a database schema for a user profile feature might inadvertently break the compilation of a shopping cart feature due to shared transitive dependencies or entangled data models. This interconnectivity forces developers to compile the entire application to verify even minor changes, leading to excessive feedback loops and decreased productivity.2

Modularization addresses these systemic inefficiencies by reorganizing the codebase into loosely coupled, self-contained units. The "Micro-App" architecture extends this concept by treating each feature module not just as a library of code, but as a potential application in its own right. By wrapping feature modules in lightweight "Harness" or "Shell" applications, developers can run a specific feature (e.g., the Checkout flow) on a device without compiling the rest of the application (e.g., User Profile, Search, Recommendations).1

#### **1.1.1 The Stratification of Modules**

To achieve a system where components can be combined at will, the architecture must define strict layers of responsibility. A successful micro-app implementation typically categorizes modules into four distinct stratums, creating a directed acyclic graph (DAG) of dependencies 3:

| Module Stratum | Role & Responsibility | Dependency Constraints | Build Artifact Type |
| :---- | :---- | :---- | :---- |
| **App Module** (:app) | The integration root. It orchestrates the startup flow, dependency injection graph, and build variants. It contains minimal logic. | Depends on all Feature Modules and Core Modules. | com.android.application |
| **Feature Modules** (:feature:x) | Self-contained functional units (e.g., Login, Dashboard). Contains UI, ViewModels, and Domain logic. | Depends on :core modules. **Must not** depend on other Feature Modules. | com.android.library (or dynamic-feature) |
| **Core Modules** (:core, :library) | Shared infrastructure used by all features (e.g., Networking, Database, Design System, Analytics). | Depends only on other Core modules or third-party libraries. | com.android.library |
| **Harness Modules** (:harness:x) | Lightweight wrappers designed to run a specific Feature Module in isolation during development. | Depends on the specific :feature module and :core modules. | com.android.application |

The rigorous enforcement of these boundaries allows features to be "combined at will." The :app module acts as a configuration file, selecting which features to include in the final build. This is particularly powerful for creating different flavors of the application—such as a "Driver" app vs. a "Rider" app, or a "Free" version vs. a "Pro" version—reusing the same underlying feature blocks.2

### **1.2 The "Run Alone" Capability**

The requirement to run individual pieces of an app "all alone" fundamentally changes the development lifecycle. In a standard multi-module setup, a feature module is simply a library; it cannot be installed on a device. To make it runnable, the architecture must provide an execution environment that simulates the necessary conditions of the main application. This is achieved through **Harness Applications**.6

A Harness Application is a disposable, debug-only APK that serves as a host for a Feature Module. It provides:

1. **An Entry Point:** A LAUNCHER Activity that navigates immediately to the feature's starting screen.  
2. **A Mock Environment:** Implementations of core dependencies (like Authentication or Analytics) that the feature expects from the main app but are not present in the isolated library.  
3. **Specific Configurations:** Debug settings, network proxies, or developer tools (like LeakCanary) that facilitate deep testing of that specific feature.

This approach decouples the development of a feature from the stability of the entire system. If the "Search" feature is broken, a developer working on "Payments" remains unaffected because they are running the payments-harness app, not the main :app.8

## **2\. The Build System: Engineering Gradle for Isolation**

The enabler of this modular flexibility is the Gradle build system. Achieving a setup where dozens of modules can be managed consistently, and where specific modules can be toggled into runnable states, requires advanced configuration using the Kotlin DSL (.kts), Version Catalogs, and Convention Plugins.9

### **2.1 Centralizing Logic with Convention Plugins**

In a project with 50+ modules, copying and pasting build.gradle.kts configuration logic (such as setting the compileSdk, applying the Kotlin plugin, or configuring strict mode options) leads to significant technical debt and "configuration drift." If one module uses minSdk \= 24 and another minSdk \= 26, merging them into a final app can cause manifest merger errors or runtime crashes.10

**Gradle Convention Plugins**, typically defined in a build-logic included build, provide a scalable solution. These plugins allow the engineering team to define "Types" of modules. For a micro-app architecture, one would define specific conventions for Feature Modules versus Harness Modules.11

#### **2.1.1 Anatomy of a Feature Convention Plugin**

A AndroidFeatureConventionPlugin encapsulates the configuration for any module that represents a user-facing feature. It automatically applies the Android Library plugin, the Kotlin Android plugin, and sets up common dependencies like Hilt and Coroutines.

Kotlin

// build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt  
class AndroidFeatureConventionPlugin : Plugin\<Project\> {  
    override fun apply(target: Project) {  
        with(target) {  
            pluginManager.apply("com.android.library")  
            pluginManager.apply("org.jetbrains.kotlin.android")  
            pluginManager.apply("com.google.dagger.hilt.android")

            extensions.configure\<LibraryExtension\> {  
                defaultConfig {  
                    testInstrumentationRunner \= "com.example.core.testing.HiltTestRunner"  
                }  
                // Configure common build types, Java versions, and resources  
                configureAndroidCommon(this)  
            }

            dependencies {  
                add("implementation", project(":core:model"))  
                add("implementation", project(":core:ui"))  
                add("implementation", project(":core:data"))  
                // Common feature dependencies  
                add("implementation", libs.findLibrary("androidx.hilt.navigation.compose").get())  
            }  
        }  
    }  
}

By applying id("my.android.feature") in a module's build file, the developer guarantees that the module is correctly configured to operate within the ecosystem. This standardization is critical when creating "individual pieces" because it ensures that every piece speaks the same build language.12

### **2.2 Dependency Management with Version Catalogs**

To combine modules "at will" without conflict, they must share precise dependency versions. Gradle Version Catalogs (standardized in libs.versions.toml) provide a type-safe mechanism to declare these dependencies centrally.9

The libs.versions.toml file acts as the single source of truth for the entire dependency graph:

Ini, TOML

\[versions\]  
agp \= "8.2.0"  
kotlin \= "1.9.20"  
compose \= "1.5.4"  
hilt \= "2.48"

\[libraries\]  
androidx-core-ktx \= { group \= "androidx.core", name \= "core-ktx", version \= "1.12.0" }  
hilt-android \= { group \= "com.google.dagger", name \= "hilt-android", version.ref \= "hilt" }  
hilt-compiler \= { group \= "com.google.dagger", name \= "hilt-android-compiler", version.ref \= "hilt" }

\[plugins\]  
android-application \= { id \= "com.android.application", version.ref \= "agp" }  
android-library \= { id \= "com.android.library", version.ref \= "agp" }  
hilt \= { id \= "com.google.dagger.hilt.android", version.ref \= "hilt" }

When a Harness app runs a feature, it implicitly relies on this catalog to resolve the transitive dependencies of the feature module. This prevents the "Diamond Dependency" problem where the harness requests version 1.0 of a library but the feature requests version 2.0, causing runtime crashes in the isolated environment.9

### **2.3 Strategies for "Run Alone" Configuration**

There are two primary architectural strategies to achieve the capability of running a module in isolation: the **Wrapper Strategy** (recommended) and the **Toggle Strategy** (legacy/complex).

#### **2.3.1 The Wrapper Strategy: Explicit Harness Modules**

This strategy advocates for a 1-to-1 relationship between a Feature Module (Library) and a Harness Module (Application).

* **Structure:**  
  * features/login/library/ (The code)  
  * features/login/app/ (The harness)  
* **Advantages:** It creates a clean separation of concerns. The library remains a pure library, compilable by the main app. The harness can contain "dirty" code—shortcuts, debug drawers, and mock data loaders—that should never reach production. It also allows the harness to have a distinct AndroidManifest.xml that declares the LAUNCHER intent, which is merged with the library's manifest.1  
* **Implementation:** The harness build.gradle.kts simply depends on the library:  
  Kotlin  
  dependencies {  
      implementation(project(":features:login:library"))  
  }

#### **2.3.2 The Toggle Strategy: Dynamic Plugin Application**

This strategy attempts to use a single module that acts as a Library by default but "transforms" into an Application when a flag is passed to Gradle.15

* **Mechanism:**  
  Kotlin  
  val isStandalone \= project.hasProperty("standalone")  
  plugins {  
      if (isStandalone) {  
          id("com.android.application")  
      } else {  
          id("com.android.library")  
      }  
  }

* **Disadvantages:** This approach is notoriously fragile. It confuses the Android Studio IDE (which struggles to index symbols that change type based on dynamic flags) and complicates the plugins {} block, which has strict syntax limitations.16 While it reduces the folder count, it increases build complexity and fragility, making the Wrapper Strategy superior for robust modular systems.1

## **3\. Designing the Isolated Execution Environment (The Harness)**

A Harness Module is not simply a container; it is a simulator. To run a feature "all alone," the harness must synthesize the application environment that the feature expects.

### **3.1 Manifest Merging and Entry Points**

Android applications require a defined entry point, typically an Activity with the android.intent.action.MAIN and android.intent.category.LAUNCHER intent filters. Feature modules, being libraries, do not declare this.

The Harness module's AndroidManifest.xml serves as the vehicle to inject this entry point. It can take two forms:

1. **Direct Activity Launch:** If the feature module is Single-Activity based (using Fragments or Composables), the Harness must provide a container Activity (HarnessActivity) that hosts the feature's UI.  
2. **Deep Link Launch:** If the architecture relies on deep linking, the Harness can simply trigger the deep link URI in its onCreate method to route the user to the feature's graph.17

### **3.2 Handling Cross-Module Navigation in Isolation**

One of the most significant challenges in modular development is navigation. In the main app, the "Login" feature navigates to the "Home" feature. However, in the "Login Harness," the "Home" feature does not exist. If the code directly references HomeActivity, the harness will fail to compile.

To resolve this, navigation must be decoupled using **Interfaces** and **Dependency Injection**.18

#### **3.2.1 The Navigator Interface Pattern**

This pattern defines navigation contracts in a shared :core:navigation module, which both the feature and the harness depend on.

1. **Define the Contract (:core:navigation):**  
   Kotlin  
   interface LoginNavigation {  
       fun navigateToHome()  
       fun navigateToRegistration()  
   }

2. Consume the Contract (:feature:login):  
   The Login ViewModel or Fragment injects this interface.  
   Kotlin  
   class LoginViewModel @Inject constructor(  
       private val navigator: LoginNavigation  
   ) : ViewModel() {  
       fun onLoginSuccess() {  
           navigator.navigateToHome()  
       }  
   }

3. Implement in Production (:app):  
   The main app provides an implementation that performs the actual navigation (e.g., using Jetpack Navigation NavController or Intents).  
4. Implement in Isolation (:feature:login:app):  
   The Harness app provides a Mock or Stub implementation.  
   Kotlin  
   class HarnessLoginNavigation @Inject constructor() : LoginNavigation {  
       override fun navigateToHome() {  
           Log.d("Harness", "Navigation to Home triggered")  
           // Optional: Show a Toast to verify the action visually  
       }  
   }

This architectural pattern ensures that the "Login" feature can be run and tested alone. The developer verifies that the "Home" navigation event is fired, satisfying the feature's functional requirement without needing the actual "Home" module present.19

### **3.3 Deep Link Decoupling**

Another robust approach for combining features at will is implicit Deep Linking. By utilizing URIs (e.g., myapp://feature/id) as the primary navigation mechanism, modules become completely agnostic of one another.17

In the Harness environment, triggering a deep link is trivial. The developer can use the Android Debug Bridge (ADB) to fire intents into the running harness:  
adb shell am start \-W \-a android.intent.action.VIEW \-d "myapp://login" com.example.login.harness.6 This reinforces the isolation, as the feature module relies only on the URI string contract, not on any Java/Kotlin class linkage.

## **4\. Dependency Injection: The Glue of Modular Systems**

Dependency Injection (DI) is the backbone of decoupled architecture, but it presents unique challenges in a multi-module environment where the graph is fragmented. Google's Hilt library is the industry standard, yet its reliance on a global SingletonComponent generated at the Application level requires careful adaptation for isolated harness apps.22

### **4.1 The Hilt SingletonComponent Dilemma**

Hilt operates by generating a dependency graph rooted in a single class annotated with @HiltAndroidApp. This class aggregates all modules annotated with @InstallIn(SingletonComponent::class) found in the compilation classpath.

* In the **Production App**, the classpath includes all features, so the graph is complete.  
* In the **Harness App**, the classpath includes only the isolated feature and core modules.

If the Feature Module depends on a binding that is typically provided by the :app module (such as a global AnalyticsTracker implementation), the Harness App will fail to compile because the graph is missing that dependency.24

### **4.2 The Custom Harness Application**

To solve the missing dependency problem, the Harness module must declare its own Application class annotated with @HiltAndroidApp. This triggers the generation of a separate, isolated DI component for the harness.22

Within the Harness module, the developer must provide **Fakes** for any missing dependencies. This is often done by defining a Hilt module in the Harness source set:

Kotlin

// In :feature:login:app source set  
@Module  
@InstallIn(SingletonComponent::class)  
object HarnessModule {  
    @Provides  
    @Singleton  
    fun provideAnalyticsTracker(): AnalyticsTracker {  
        return object : AnalyticsTracker {  
            override fun trackEvent(event: String) {  
                Log.d("HarnessAnalytics", "Event tracked: $event")  
            }  
        }  
    }  
}

This ensures that the feature module's injection sites (@Inject tracker: AnalyticsTracker) are satisfied, allowing the feature to run alone.

### **4.3 Handling Circular Dependencies with EntryPoints**

In complex modular graphs, features sometimes need dependencies that create circular references if injected directly. Or, a feature might need a dependency from a Dynamic Feature Module (DFM) which is loaded at runtime. Hilt's @EntryPoint annotation allows for accessing the DI graph without standard constructor injection, which is essential for these boundary crossings.22

For example, if a feature needs to access a legacy component or a dependency provided by a 3rd party SDK initialized in the App class, the feature can define an EntryPoint interface:

Kotlin

@EntryPoint  
@InstallIn(SingletonComponent::class)  
interface LegacyDeps {  
    fun getOldService(): OldService  
}

In the Harness app, the LegacyDeps can be implemented to return a mock OldService, maintaining the isolation.

## **5\. Data Layer Architecture: Data Isolation and Synchronization**

The request to run pieces "one at a time" faces its stiffest resistance in the Data Layer. Monolithic databases create hard couplings between features. If :feature:login and :feature:settings share a single AppDatabase class, neither can run without the other's table definitions present.26

### **5.1 Database Sharding Strategy**

To enable true modularity, the architectural consensus moves toward **Database Sharding**—giving each feature module its own local database.

* **Monolithic DB:** One RoomDatabase with 50 entities. Hard to split.  
* **Sharded DB:**  
  * LoginDatabase (User table)  
  * CartDatabase (Product, Order tables)  
  * ContentDatabase (Article, Video tables)

By implementing sharding, the Login Harness App only initializes the LoginDatabase. It does not need to know about the Cart schema. This aligns perfectly with the "run alone" requirement.

### **5.2 The "Dropbox Store" Pattern for Data Flow**

Managing data flow in a modular app requires robust caching and source-of-truth management. The **Dropbox Store** library (now part of Mobile Native Foundation) provides a pattern for this, which is highly relevant for modular apps.27

Store acts as a repository implementation that isolates the data fetching logic. It handles memory caching, disk caching (Source of Truth), and network fetching (Fetcher).

* **Mechanism:** Store\<Key, Output\>  
* **Relevance to Modularization:** A feature module can define a Store for its specific data type. The Store encapsulates the specific Retrofit service and Room DAO for that feature.  
* **Isolation:** When running in a Harness, the Store can be configured to bypass the network or seed the disk cache with dummy data, allowing the UI to be developed against a predictable data state without a live backend.29

### **5.3 Cross-Module Data Integrity**

The tradeoff of sharding is the loss of SQL joins and Foreign Keys across boundaries. You cannot JOIN users ON orders.user\_id \= users.id if they are in different database files.

The architecture must handle this aggregation at the **Domain Layer** or **Use Case Layer** in memory.

* **Composite Data Objects:** A UserWithOrders object is constructed by fetching the User from the UserRepository and Orders from the OrderRepository, then combining them using Reactive streams (RxJava zip or Coroutines combine).  
* **Data Synchronization:** If a user logs out (clearing LoginDatabase), the system must explicitly notify other modules to clear their data. This is typically handled via an **Event Bus** or a **SessionScope** manager in the :core module that all features observe.26

## **6\. UI Development in Isolation: Jetpack Compose and Showkase**

The introduction of Jetpack Compose has revolutionized the granularity of modularization. With Compose, the unit of UI is no longer the Activity or Fragment, but the @Composable function. This allows for even finer "individual pieces" to be run alone.

### **6.1 Airbnb Showkase: A Component Browser**

While Harness Apps run a full feature flow, developers often need to see and test individual UI components (buttons, cards, error states) in isolation. **Airbnb Showkase** is an annotation-processing library designed specifically for this purpose in modular builds.30

Showkase aggregates all composables annotated with @Preview across the module graph and organizes them into a browser application.

* **Setup in Harness:** The Harness module includes the Showkase dependency and a root module implementation.  
  Kotlin  
  @ShowkaseRoot  
  class HarnessRootModule : ShowkaseRootModule

* **Execution:** When the Harness App runs, instead of launching the feature directly, it can launch the Showkase Browser Intent.  
* **Benefit:** This provides a searchable catalog of every UI element in the feature module. It allows the developer to verify "Dark Mode," "Font Scaling," and "RTL Layouts" for a specific component without navigating through the app to reach it.32

### **6.2 Server-Driven UI and Plaid's Dynamic Approach**

The **Plaid** application (Google's reference design) and **Now in Android** demonstrate advanced modularization where UI can be dynamic. In these architectures, feature modules (like "About" or "Search") are designed as **Dynamic Feature Modules** that can be downloaded on demand.33

* **Implication for Harnesses:** Testing dynamic modules locally requires bundletool to simulate the installation of splits.34  
* **Plaid's Lesson:** Plaid moved code to a :core library to share dependencies, but kept feature logic distinct. The "Run Alone" capability was essential for iterating on the complex transitions in Plaid's news feed without compiling the full app.35  
* **Airbnb's Server-Driven UI:** By standardizing UI components (via Showkase/Compose) and modularizing features, apps can render UI based on JSON responses from the server, mapping server keys to local modular components.36 This requires the Harness app to have a "JSON Injector" debug tool to simulate different server responses.

## **7\. Testing Strategy: verifying "Combined at Will"**

The ability to combine modules at will implies that they are verified to work independently. Modularization transforms the testing pyramid, shifting focus from End-to-End (E2E) tests to Isolated Integration tests.

### **7.1 Isolated Instrumentation Testing**

In a monolithic app, instrumentation tests (androidTest) are slow because they involve the whole APK. In a modular setup, tests run against the specific feature module context.37

To execute these tests, Hilt requires a **Custom Test Runner**. This runner replaces the standard Application class with a HiltTestApplication for the duration of the test, ensuring that a fresh, isolated DI graph is generated for every test case.7

Kotlin

// In :feature:login/src/androidTest/java/  
class HiltTestRunner : AndroidJUnitRunner() {  
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {  
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)  
    }  
}

This setup allows developers to write Espresso or Compose tests that inject mock Repositories directly into the Feature's ViewModel, verifying the UI logic in milliseconds.

### **7.2 CI/CD Impact and Build Caching**

Modularization allows Continuous Integration (CI) systems to be smarter. By using Gradle's **Task Configuration Avoidance** and **Impact Analysis**, the CI system can detect which modules have changed in a Pull Request.

* **Scenario:** A developer modifies :feature:login.  
* **Impact:** The CI runs tests for :feature:login and :app. It *skips* tests for :feature:settings and :feature:dashboard.  
* **Result:** Drastic reduction in CI wait times.  
* **Remote Build Cache:** When a developer switches branches, Gradle pulls the pre-compiled artifacts of unchanged modules from a shared remote cache, meaning the "combine at will" step (linking) happens almost instantly.3

## **8\. Case Studies: Google's "Now in Android" and "Plaid"**

Analyzing industry-standard reference implementations provides concrete validation of these patterns.

### **8.1 Now in Android (NiA)**

Google's **Now in Android** app is the modern canonical example of modular architecture.40

* **Separation of API and Implementation:** NiA splits features into :feature:x (implementation) and :core:model (data). It avoids direct dependency between features.  
* **Navigation:** It uses a simplified navigation pattern where the App module binds the navigation graph, but features remain isolated.  
* **Takeaway:** NiA prioritizes "Sync" logic in a background worker located in a core module, ensuring that data freshness is decoupled from UI features. This allows the UI features to be purely reactive, simplifying their Harness implementation.42

### **8.2 Plaid**

**Plaid** represents the transition from legacy to modular. It demonstrated the migration of a monolith by extracting "News Sources" (Designer News, Dribbble) into dynamic features.33

* **Dynamic Loading:** Plaid utilized on-demand delivery, meaning the "Create Post" feature wasn't even on the user's device until requested.  
* **Takeaway:** This required rigorous interface abstraction. The "Post" button in the main UI had to handle the state where the "Post Feature" was not yet installed, a complexity that Harness apps must simulate via "Fake Install Managers."

## **9\. Conclusion: The Strategic Value of Isolation**

The transition to a modularized Android architecture, characterized by self-contained micro-apps, is a strategic enabler for scalability. By adhering to the principles of **Dependency Inversion**, **Database Sharding**, and **Harness-Driven Development**, organizations can decouple their engineering teams and software components.

The capability to run pieces "one at a time" serves as the ultimate litmus test for architectural health. If a feature can be launched in a harness, injected with mock data, and operated without crashing, it proves that the business logic is truly decoupled from the monolithic frame. While this approach introduces initial complexity in Gradle configuration and DI setup, the long-term dividends in build velocity, test stability, and architectural flexibility make it the gold standard for modern Android development.

### **Summary of Recommendations**

1. **Adopt the Wrapper Strategy:** Create explicit :harness modules for every significant feature.  
2. **Shard Data:** Avoid monolithic databases; give features their own persistence layer.  
3. **Standardize Builds:** Use Gradle Convention Plugins and Version Catalogs to keep module configurations identical.  
4. **Isolate Navigation:** Use Interface-based navigation or Deep Links to prevent compile-time coupling.  
5. **Visualize Components:** Integrate Showkase in Harness apps to speed up UI iteration.

This architecture transforms the application from a singular, fragile block into a fleet of independent, robust, and combinable vessels.

#### **Works cited**

1. Meet the microapps architecture – Increment, accessed December 19, 2025, [https://increment.com/mobile/microapps-architecture/](https://increment.com/mobile/microapps-architecture/)  
2. Guide to Android app modularization | App architecture, accessed December 19, 2025, [https://developer.android.com/topic/modularization](https://developer.android.com/topic/modularization)  
3. Designing Multi-Module Android Applications: A Comprehensive ..., accessed December 19, 2025, [https://medium.com/@sharmapraveen91/designing-multi-module-android-applications-a-comprehensive-guide-1124d4b4c6c3](https://medium.com/@sharmapraveen91/designing-multi-module-android-applications-a-comprehensive-guide-1124d4b4c6c3)  
4. vmadalin/android-modular-architecture: Sample Android ... \- GitHub, accessed December 19, 2025, [https://github.com/vmadalin/android-modular-architecture](https://github.com/vmadalin/android-modular-architecture)  
5. Modularization of Android Applications in 2021 | by Andrei Beriukhov | ProAndroidDev, accessed December 19, 2025, [https://proandroiddev.com/modularization-of-android-applications-in-2021-a79a590d5e5b](https://proandroiddev.com/modularization-of-android-applications-in-2021-a79a590d5e5b)  
6. Implement Test Harness Mode \- Android Open Source Project, accessed December 19, 2025, [https://source.android.com/docs/core/tests/debug/harness](https://source.android.com/docs/core/tests/debug/harness)  
7. Hilt testing guide | App architecture \- Android Developers, accessed December 19, 2025, [https://developer.android.com/training/dependency-injection/hilt-testing](https://developer.android.com/training/dependency-injection/hilt-testing)  
8. Common modularization patterns | App architecture \- Android Developers, accessed December 19, 2025, [https://developer.android.com/topic/modularization/patterns](https://developer.android.com/topic/modularization/patterns)  
9. Migrate your build to version catalogs | Android Studio, accessed December 19, 2025, [https://developer.android.com/build/migrate-to-catalogs](https://developer.android.com/build/migrate-to-catalogs)  
10. Gradle Convention Plugin for Android Developers — The Practical Beginner's Guide | by Olubunmi Alegbeleye | Oct, 2025 | Medium, accessed December 19, 2025, [https://medium.com/@olubunmi-alegbeleye/gradle-convention-plugin-for-android-developers-the-practical-beginners-guide-db84f5b10bdd](https://medium.com/@olubunmi-alegbeleye/gradle-convention-plugin-for-android-developers-the-practical-beginners-guide-db84f5b10bdd)  
11. Modularization in Android \- gradle \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/76870335/modularization-in-android](https://stackoverflow.com/questions/76870335/modularization-in-android)  
12. Sharing Build Logic using buildSrc \- Gradle User Manual, accessed December 19, 2025, [https://docs.gradle.org/current/userguide/sharing\_build\_logic\_between\_subprojects.html](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)  
13. Sharing build logic in a multi-repo setup Sample \- Gradle User Manual, accessed December 19, 2025, [https://docs.gradle.org/current/samples/sample\_publishing\_convention\_plugins.html](https://docs.gradle.org/current/samples/sample_publishing_convention_plugins.html)  
14. Create a standalone library module in Android studio \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/30695132/create-a-standalone-library-module-in-android-studio](https://stackoverflow.com/questions/30695132/create-a-standalone-library-module-in-android-studio)  
15. Apply gradle plugin conditionally based on productFlavor in Kotlin DSL \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/79779741/apply-gradle-plugin-conditionally-based-on-productflavor-in-kotlin-dsl](https://stackoverflow.com/questions/79779741/apply-gradle-plugin-conditionally-based-on-productflavor-in-kotlin-dsl)  
16. How to optionally apply some plugins using Kotlin DSL and plugins block with gradle and android \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/62579114/how-to-optionally-apply-some-plugins-using-kotlin-dsl-and-plugins-block-with-gra](https://stackoverflow.com/questions/62579114/how-to-optionally-apply-some-plugins-using-kotlin-dsl-and-plugins-block-with-gra)  
17. Navigation with Compose | Jetpack Compose \- Android Developers, accessed December 19, 2025, [https://developer.android.com/develop/ui/compose/navigation](https://developer.android.com/develop/ui/compose/navigation)  
18. How do we handle multi-modules navigation on our Android app | by Philippe BOISNEY, accessed December 19, 2025, [https://engineering.backmarket.com/how-we-handle-multi-modules-navigation-on-our-android-app-25319e62d219](https://engineering.backmarket.com/how-we-handle-multi-modules-navigation-on-our-android-app-25319e62d219)  
19. Android Multimodule Navigation with the Navigation Component | by Dimitar Dihanov, accessed December 19, 2025, [https://itnext.io/android-multimodule-navigation-with-the-navigation-component-99f265de24](https://itnext.io/android-multimodule-navigation-with-the-navigation-component-99f265de24)  
20. Navigation in multi-module Android project | by Oscar Caballero | Ninety Nine Product & Tech | Medium, accessed December 19, 2025, [https://medium.com/ninetyniners/navigation-in-multi-module-android-project-33d14b91f08](https://medium.com/ninetyniners/navigation-in-multi-module-android-project-33d14b91f08)  
21. Is there a way to create implicit deeplink in Android Multi-module project using Navigation Component \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/55901880/is-there-a-way-to-create-implicit-deeplink-in-android-multi-module-project-using](https://stackoverflow.com/questions/55901880/is-there-a-way-to-create-implicit-deeplink-in-android-multi-module-project-using)  
22. Hilt in multi-module apps | App architecture \- Android Developers, accessed December 19, 2025, [https://developer.android.com/training/dependency-injection/hilt-multi-module](https://developer.android.com/training/dependency-injection/hilt-multi-module)  
23. Dependency injection with Hilt | App architecture \- Android Developers, accessed December 19, 2025, [https://developer.android.com/training/dependency-injection/hilt-android](https://developer.android.com/training/dependency-injection/hilt-android)  
24. How to support modularization in Android app with HILT? \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/65735142/how-to-support-modularization-in-android-app-with-hilt](https://stackoverflow.com/questions/65735142/how-to-support-modularization-in-android-app-with-hilt)  
25. 7 Hidden Hilt Concepts Every Android Developer Should Know (Used in Large-Scale Apps) \! | by Prabhanshu Lakshakar \- Medium, accessed December 19, 2025, [https://medium.com/@PrabhanshuLakshakar/7-hidden-hilt-concepts-every-android-developer-should-know-used-in-large-scale-apps-9bb4ab270121](https://medium.com/@PrabhanshuLakshakar/7-hidden-hilt-concepts-every-android-developer-should-know-used-in-large-scale-apps-9bb4ab270121)  
26. Best Approach for Database Structure in a Multi-Module Android App? \- Reddit, accessed December 19, 2025, [https://www.reddit.com/r/androiddev/comments/1izym8f/best\_approach\_for\_database\_structure\_in\_a/](https://www.reddit.com/r/androiddev/comments/1izym8f/best_approach_for_database_structure_in_a/)  
27. Modern Android App Architecture with Dropbox Store and JetPack \- Appinventiv, accessed December 19, 2025, [https://appinventiv.com/blog/jetpack-and-dropbox-store-android-app-architecture/](https://appinventiv.com/blog/jetpack-and-dropbox-store-android-app-architecture/)  
28. Store grand re-opening: loading Android data with coroutines \- Dropbox Tech Blog, accessed December 19, 2025, [https://dropbox.tech/mobile/store-grand-re-opening-loading-android-data-with-coroutines](https://dropbox.tech/mobile/store-grand-re-opening-loading-android-data-with-coroutines)  
29. Is anyone using the Dropbox Store library in Android? : r/androiddev \- Reddit, accessed December 19, 2025, [https://www.reddit.com/r/androiddev/comments/u9edk8/is\_anyone\_using\_the\_dropbox\_store\_library\_in/](https://www.reddit.com/r/androiddev/comments/u9edk8/is_anyone_using_the_dropbox_store_library_in/)  
30. Showkase is an annotation-processor based Android library that helps you organize, discover, search and visualize Jetpack Compose UI elements \- GitHub, accessed December 19, 2025, [https://github.com/airbnb/Showkase](https://github.com/airbnb/Showkase)  
31. Introducing Showkase: A Library to Organize, Discover, and Visualize Your Jetpack Compose Elements | by Vinay Gaba | The Airbnb Tech Blog | Medium, accessed December 19, 2025, [https://medium.com/airbnb-engineering/introducing-showkase-a-library-to-organize-discover-and-visualize-your-jetpack-compose-elements-d5c34ef01095](https://medium.com/airbnb-engineering/introducing-showkase-a-library-to-organize-discover-and-visualize-your-jetpack-compose-elements-d5c34ef01095)  
32. Visualize Android Components with Airbnb's ShowKase. | by kanake \- Medium, accessed December 19, 2025, [https://medium.com/@10kanake/visualize-android-components-with-airbnbs-showkase-e68821436439](https://medium.com/@10kanake/visualize-android-components-with-airbnbs-showkase-e68821436439)  
33. Patchwork Plaid — A modularization story | by Ben Weiss | Android Developers | Medium, accessed December 19, 2025, [https://medium.com/androiddevelopers/a-patchwork-plaid-monolith-to-modularized-app-60235d9f212e](https://medium.com/androiddevelopers/a-patchwork-plaid-monolith-to-modularized-app-60235d9f212e)  
34. Android Dynamic Feature Module — Complete Guide \- Medium, accessed December 19, 2025, [https://medium.com/@aanshul16/android-dynamic-feature-module-complete-guide-e2844717c2c8](https://medium.com/@aanshul16/android-dynamic-feature-module-complete-guide-e2844717c2c8)  
35. The ABC of Modularization for Android in 2021 | by Christopher Elias | ProAndroidDev, accessed December 19, 2025, [https://proandroiddev.com/the-abc-of-modularization-for-android-in-2021-e7b3fbe29fca](https://proandroiddev.com/the-abc-of-modularization-for-android-in-2021-e7b3fbe29fca)  
36. A New Architecture for Plaid Link: Server-Driven UI with Directed Graphs, accessed December 19, 2025, [https://plaid.com/blog/a-new-architecture-for-plaid-link-server-driven-ui-with-directed-graphs/](https://plaid.com/blog/a-new-architecture-for-plaid-link-server-driven-ui-with-directed-graphs/)  
37. Integration Testing on Android: A Practical Guide with Hilt, Compose & Room \- droidcon, accessed December 19, 2025, [https://www.droidcon.com/2025/08/19/integration-testing-on-android-a-practical-guide-with-hilt-compose-room/](https://www.droidcon.com/2025/08/19/integration-testing-on-android-a-practical-guide-with-hilt-compose-room/)  
38. Set Up Android Jetpack Compose Instrumentation Test with Hilt \- Medium, accessed December 19, 2025, [https://medium.com/@danimahardhika/setup-android-jetpack-compose-instrumentation-test-with-hilt-e01a9d21b436](https://medium.com/@danimahardhika/setup-android-jetpack-compose-instrumentation-test-with-hilt-e01a9d21b436)  
39. Configure your build | Android Studio, accessed December 19, 2025, [https://developer.android.com/build](https://developer.android.com/build)  
40. nowinandroid/docs/ModularizationLearningJourney.md at main \- GitHub, accessed December 19, 2025, [https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)  
41. android/nowinandroid: A fully functional Android app built entirely with Kotlin and Jetpack Compose \- GitHub, accessed December 19, 2025, [https://github.com/android/nowinandroid](https://github.com/android/nowinandroid)  
42. \[Bug\]: Breaking SOLID principles and Clean Architecture. · android nowinandroid · Discussion \#1273 \- GitHub, accessed December 19, 2025, [https://github.com/android/nowinandroid/discussions/1273](https://github.com/android/nowinandroid/discussions/1273)