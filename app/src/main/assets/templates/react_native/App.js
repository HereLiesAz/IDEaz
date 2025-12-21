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

  const toggleTodo = (id) => {
    const updatedTodos = todos.map(t => t.id === id ? { ...t, completed: !t.completed } : t);
    setTodos(updatedTodos);
    saveTodos(updatedTodos);
  };

  const deleteTodo = (id) => {
    const updatedTodos = todos.filter(t => t.id !== id);
    setTodos(updatedTodos);
    saveTodos(updatedTodos);
  };

  const renderItem = ({ item }) => (
    <View style={styles.itemContainer}>
      <TouchableOpacity onPress={() => toggleTodo(item.id)} style={styles.itemTextContainer}>
        <Text style={[styles.itemText, item.completed && styles.itemTextCompleted]}>
          {item.title}
        </Text>
      </TouchableOpacity>
      <Button title="Del" color="red" onPress={() => deleteTodo(item.id)} />
    </View>
  );

  return (
    <View style={styles.container}>
      <Text style={styles.header}>ToDo List</Text>
      <View style={styles.inputContainer}>
        <TextInput
          style={styles.input}
          placeholder="New Task..."
          value={text}
          onChangeText={setText}
        />
        <Button title="Add" onPress={addTodo} />
      </View>
      <FlatList
        data={todos}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        style={styles.list}
      />
      <Button
        title="Go to Details"
        onPress={() => navigation.navigate('Details')}
      />
    </View>
  );
};

const DetailsScreen = ({ navigation }) => {
  return (
    <View style={styles.container}>
      <Text style={styles.header}>Details Screen</Text>
      <Text style={styles.text}>This demonstrates navigation!</Text>
      <Button
        title="Go Back"
        onPress={() => navigation.goBack()}
      />
    </View>
  );
};

const App = () => {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="Details" component={DetailsScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  inputContainer: {
    flexDirection: 'row',
    marginBottom: 20,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 10,
    marginRight: 10,
    borderRadius: 5,
  },
  list: {
    flex: 1,
  },
  itemContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    padding: 10,
    backgroundColor: '#f9f9f9',
    borderRadius: 5,
  },
  itemTextContainer: {
    flex: 1,
  },
  itemText: {
    fontSize: 18,
  },
  itemTextCompleted: {
    textDecorationLine: 'line-through',
    color: '#aaa',
  },
  text: {
      fontSize: 16,
      textAlign: 'center',
      marginTop: 20
  }
});

export default App;
