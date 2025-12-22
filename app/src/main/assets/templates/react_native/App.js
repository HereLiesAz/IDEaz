import React, { useState } from 'react';
import { View, Text, Button, StyleSheet, NativeModules } from 'react-native';

const App = () => {
  const [count, setCount] = useState(0);

  const increment = () => {
    setCount(count + 1);
    if (NativeModules.IdeazModule) {
        NativeModules.IdeazModule.showToast('Count: ' + (count + 1));
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Hello React Native!</Text>
      <Text style={styles.text}>Count: {count}</Text>
      <Button title="Increment" onPress={increment} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  text: {
    fontSize: 18,
    marginBottom: 20,
  },
});

export default App;
