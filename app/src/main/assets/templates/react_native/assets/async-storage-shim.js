const AsyncStorage = {
    getItem: (key) => {
        return new Promise((resolve, reject) => {
            try {
                const value = window.localStorage.getItem(key);
                resolve(value);
            } catch (e) {
                reject(e);
            }
        });
    },
    setItem: (key, value) => {
        return new Promise((resolve, reject) => {
            try {
                window.localStorage.setItem(key, value);
                resolve();
            } catch (e) {
                reject(e);
            }
        });
    },
    removeItem: (key) => {
        return new Promise((resolve, reject) => {
            try {
                window.localStorage.removeItem(key);
                resolve();
            } catch (e) {
                reject(e);
            }
        });
    },
    clear: () => {
        return new Promise((resolve, reject) => {
            try {
                window.localStorage.clear();
                resolve();
            } catch (e) {
                reject(e);
            }
        });
    },
    getAllKeys: () => {
         return new Promise((resolve, reject) => {
            try {
                const keys = [];
                for (let i = 0; i < window.localStorage.length; i++) {
                    keys.push(window.localStorage.key(i));
                }
                resolve(keys);
            } catch (e) {
                reject(e);
            }
        });
    }
};

export default AsyncStorage;
