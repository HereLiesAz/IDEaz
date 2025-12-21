package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SimpleJsBundlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var bundler: SimpleJsBundler
    private lateinit var projectDir: File
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        bundler = SimpleJsBundler()
        projectDir = tempFolder.newFolder("project")
        outputDir = tempFolder.newFolder("output")
    }

    @Test
    fun `bundle returns failure when app json is missing`() {
        val result = bundler.bundle(projectDir, outputDir)
        assertFalse(result.success)
        assertEquals("app.json not found", result.output)
    }

    @Test
    fun `bundle returns failure when App js is missing`() {
        File(projectDir, "app.json").writeText("""{"name": "TestApp"}""")
        val result = bundler.bundle(projectDir, outputDir)
        assertFalse(result.success)
        assertEquals("App.js not found", result.output)
    }

    @Test
    fun `bundle successfully creates bundle file`() {
        File(projectDir, "app.json").writeText("""{"name": "MyTestApp"}""")
        File(projectDir, "App.js").writeText("""
            import React from 'react';
            import { View, Text } from 'react-native';
            const App = () => <View><Text>Hello</Text></View>;
            export default App;
        """.trimIndent())

        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundle should be successful: ${result.output}", result.success)
        assertTrue(result.output.contains("Bundled successfully"))

        val bundleFile = File(outputDir, "index.android.bundle")
        assertTrue("Bundle file should exist", bundleFile.exists())

        val content = bundleFile.readText()
        assertTrue("Should register correct component name", content.contains("AppRegistry.registerComponent('MyTestApp', () => App);"))
        assertTrue("Should import AppRegistry", content.contains("import { AppRegistry } from 'react-native';"))

        // Check for source mapping injection (Simple regex check used in Bundler)
        // <View> should become <View accessibilityLabel="__source:App.js:3__">
        assertTrue("Should inject source mapping", content.contains("accessibilityLabel=\"__source:App.js:"))
    }

    @Test
    fun `bundle uses expo name if name is missing`() {
        File(projectDir, "app.json").writeText("""{"expo": {"name": "ExpoApp"}}""")
        File(projectDir, "App.js").writeText("const App = () => {}; export default App;")

        val result = bundler.bundle(projectDir, outputDir)
        assertTrue(result.success)
        val content = File(outputDir, "index.android.bundle").readText()
        assertTrue(content.contains("AppRegistry.registerComponent('ExpoApp',"))
    }

    @Test
    fun `bundle falls back to MyReactNativeApp if no name found`() {
        File(projectDir, "app.json").writeText("""{}""")
        File(projectDir, "App.js").writeText("const App = () => {}; export default App;")

        val result = bundler.bundle(projectDir, outputDir)
        assertTrue(result.success)
        val content = File(outputDir, "index.android.bundle").readText()
        assertTrue(content.contains("AppRegistry.registerComponent('MyReactNativeApp',"))
    }

    @Test
    fun `bundle successfully bundles the Todo List App template`() {
        File(projectDir, "app.json").writeText("""{"name": "TodoApp"}""")
        val appJsContent = """
            import React from 'react';
            import { View, Text, TextInput, Button, FlatList, StyleSheet, TouchableOpacity, NativeModules } from 'react-native';
            import { NavigationContainer } from '@react-navigation/native';
            import { createNativeStackNavigator } from '@react-navigation/native-stack';
            import AsyncStorage from '@react-native-async-storage/async-storage';

            const Stack = createNativeStackNavigator();
            const STORAGE_KEY = '@todos';

            const HomeScreen = ({ navigation }) => {
              const [text, setText] = React.useState('');
              const [todos, setTodos] = React.useState([]);

              React.useEffect(() => {
                  const loadTodos = async () => {
                      try {
                          const json = await AsyncStorage.getItem(STORAGE_KEY);
                          if (json) {
                              setTodos(JSON.parse(json));
                          }
                      } catch (e) {
                          console.error('Failed to load todos', e);
                      }
                  };
                  loadTodos();
              }, []);

              const saveTodos = async (newTodos) => {
                  try {
                      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(newTodos));
                  } catch (e) {
                      console.error('Failed to save todos', e);
                  }
              };

              const addTodo = () => {
                if (!text || text.trim().length === 0) return;

                const newTodo = { id: Date.now().toString(), title: text, completed: false };
                const updatedTodos = [...todos, newTodo];
                setTodos(updatedTodos);
                saveTodos(updatedTodos);

                setText('');
                NativeModules.ToastAndroid.show('Task Added!', 0);
              };

              return (
                 <View style={styles.container}>
                     <TextInput value={text} onChangeText={setText} />
                     <Button title="Add" onPress={addTodo} />
                 </View>
              );
            };
            const styles = { container: {} };
            const App = () => <View><HomeScreen /></View>;
            export default App;
        """.trimIndent()

        File(projectDir, "App.js").writeText(appJsContent)

        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundle should be successful: ${result.output}", result.success)

        val bundleFile = File(outputDir, "index.android.bundle")
        val content = bundleFile.readText()

        // Verify source map injection in JSX
        assertTrue(content.contains("accessibilityLabel=\"__source:App.js:"))
        // Verify TextInput value prop is preserved (regex should not mangle it)
        assertTrue(content.contains("value={text}"))
        // Verify onChangeText prop is preserved
        assertTrue(content.contains("onChangeText={setText}"))
    }
}
