
let hooks = [];
let hookIndex = 0;
let renderRoot = null;

const React = {
    createElement: (tag, props, ...children) => {
        const finalProps = { ...(props || {}) };

        if (children.length > 0) {
            finalProps.children = children.length === 1 ? children[0] : children;
        }

        if (typeof tag === 'function') {
            return tag(finalProps);
        }

        const el = document.createElement(tag);

        if (finalProps) {
            if (finalProps.style) {
                const s = { ...finalProps.style };
                if (s.textDecorationLine) s.textDecoration = s.textDecorationLine;
                Object.assign(el.style, s);
            }

            if (finalProps.onClick) el.onclick = finalProps.onClick;
            if (finalProps.onInput) el.oninput = finalProps.onInput;
            if (finalProps.src) el.src = finalProps.src;
            if (finalProps.value !== undefined) el.value = finalProps.value;
            if (finalProps.type) el.type = finalProps.type;
            if (finalProps.placeholder) el.placeholder = finalProps.placeholder;
            if (finalProps.disabled) el.disabled = true;
            if (finalProps.className) el.className = finalProps.className;
            if (finalProps['aria-label']) el.setAttribute('aria-label', finalProps['aria-label']);
        }

        children.forEach(child => {
            if (child === null || child === undefined || child === false) return;
            if (typeof child === 'string' || typeof child === 'number') {
                el.appendChild(document.createTextNode(child));
            } else if (Array.isArray(child)) {
                child.forEach(c => c && el.appendChild(c));
            } else if (child.nodeType) {
                el.appendChild(child);
            } else if (child) {
                 el.appendChild(child);
            }
        });

        return el;
    },

    useState: (initialValue) => {
        const _hookIndex = hookIndex;
        if (hooks[_hookIndex] === undefined) {
            hooks[_hookIndex] = initialValue;
        }
        const setState = (newValue) => {
            const finalValue = typeof newValue === 'function' ? newValue(hooks[_hookIndex]) : newValue;
            hooks[_hookIndex] = finalValue;
            if (renderRoot) {
                setTimeout(renderRoot, 0);
            }
        };
        hookIndex++;
        return [hooks[_hookIndex], setState];
    },

    useRef: (initialValue) => {
        const _hookIndex = hookIndex;
        if (hooks[_hookIndex] === undefined) {
            hooks[_hookIndex] = { current: initialValue };
        }
        hookIndex++;
        return hooks[_hookIndex];
    },

    useEffect: (callback, deps) => {
        const _hookIndex = hookIndex;
        const oldHook = hooks[_hookIndex];
        const hasChanged = !oldHook || !deps || !oldHook.deps || deps.some((d, i) => d !== oldHook.deps[i]);

        if (hasChanged) {
            if (oldHook && oldHook.cleanup) oldHook.cleanup();
            setTimeout(() => {
                 const cleanup = callback();
                 hooks[_hookIndex].cleanup = cleanup;
            }, 0);
            hooks[_hookIndex] = { deps };
        }
        hookIndex++;
    }
};

export const View = (props) => {
    const style = { display: 'flex', flexDirection: 'column', position: 'relative', boxSizing: 'border-box', ...props.style };
    return React.createElement('div', { ...props, style });
};

export const Text = (props) => {
    return React.createElement('span', { ...props, style: { display: 'inline-block', ...props.style } });
};

export const Image = (props) => {
    const src = props.source ? (props.source.uri || props.source) : '';
    return React.createElement('img', { ...props, src, style: { maxWidth: '100%', height: 'auto', ...props.style } });
};

export const ScrollView = (props) => {
    return React.createElement('div', { ...props, style: { overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', ...props.style } });
};

export const TextInput = (props) => {
    const wrappedProps = {
        ...props,
        onInput: (e) => props.onChangeText && props.onChangeText(e.target.value),
        style: { borderWidth: '1px', borderColor: 'gray', padding: '8px', fontSize: '16px', ...props.style }
    };
    if (props.secureTextEntry) wrappedProps.type = 'password';
    delete wrappedProps.onChangeText;
    delete wrappedProps.secureTextEntry;
    return React.createElement('input', wrappedProps);
};

export const Button = (props) => {
    return React.createElement('button', {
        onClick: props.onPress,
        style: { padding: '10px', backgroundColor: props.color || '#2196F3', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', ...props.style },
        disabled: props.disabled
    }, props.title);
};

export const TouchableOpacity = (props) => {
    return React.createElement('div', {
        ...props,
        onClick: props.onPress,
        style: { cursor: 'pointer', ...props.style }
    });
};

export const Alert = {
    alert: (title, message) => window.alert(`${title}\n${message}`)
};

export const StyleSheet = {
    create: (styles) => styles,
    hairlineWidth: 1,
    absoluteFill: { position: 'absolute', top: 0, left: 0, bottom: 0, right: 0 }
};

export const AppRegistry = {
    registerComponent: (name, compProvider) => {
        console.log(`Registering component ${name}`);

        renderRoot = () => {
             hookIndex = 0;
             let root = document.getElementById('root');
             if (!root) {
                 root = document.createElement('div');
                 root.id = 'root';
                 root.style.height = '100vh';
                 root.style.display = 'flex';
                 root.style.flexDirection = 'column';
                 document.body.appendChild(root);
             }

             root.innerHTML = '';

             const Root = compProvider();
             const app = React.createElement(Root);
             root.appendChild(app);
             console.log('App re-rendered');
        };

        window.addEventListener('load', () => {
            renderRoot();
            console.log('App mounted');
        });
    }
};

export const FlatList = (props) => {
    const { data, renderItem, keyExtractor, style, contentContainerStyle } = props;
    const items = data || [];
    return React.createElement('div', {
        style: {
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            flex: 1,
            ...style,
            ...contentContainerStyle
        }
    }, items.map((item, index) => {
        const key = keyExtractor ? keyExtractor(item, index) : (item.key || index);
        const element = renderItem({ item, index });
        return React.createElement('div', { key }, element);
    }));
};

export const SectionList = (props) => {
    const { sections, renderItem, renderSectionHeader, keyExtractor, style, contentContainerStyle } = props;
    const sectionData = sections || [];

    const children = [];
    sectionData.forEach((section, sectionIndex) => {
        if (renderSectionHeader) {
            children.push(React.createElement('div', { key: `header-${sectionIndex}` }, renderSectionHeader({ section })));
        }
        (section.data || []).forEach((item, itemIndex) => {
            const key = keyExtractor ? keyExtractor(item, itemIndex) : (item.key || `${sectionIndex}-${itemIndex}`);
            children.push(React.createElement('div', { key }, renderItem({ item, index: itemIndex, section })));
        });
    });

    return React.createElement('div', {
        style: {
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            flex: 1,
            ...style,
            ...contentContainerStyle
        }
    }, children);
};

export const NavigationContainer = ({ children }) => {
    return React.createElement('div', {
        style: { flex: 1, display: 'flex', flexDirection: 'column', height: '100%' }
    }, children);
};

export const createNativeStackNavigator = () => {
    return {
        Navigator: ({ children, initialRouteName }) => {
            const [currentRoute, setCurrentRoute] = React.useState(initialRouteName || 'Home');

            const kids = Array.isArray(children) ? children : [children];
            const validScreens = kids.filter(k => k && k._isScreen);

            let targetScreen = validScreens.find(s => s.props.name === currentRoute);
            if (!targetScreen && validScreens.length > 0) targetScreen = validScreens[0];

            if (!targetScreen) return null;

            const navigation = {
                navigate: (route) => setCurrentRoute(route),
                goBack: () => console.log('goBack not impl')
            };

            return React.createElement(targetScreen.props.component, { navigation });
        },
        Screen: (props) => ({ _isScreen: true, props })
    };
};

export const NativeModules = {
    ToastAndroid: {
        show: (message, duration) => {
            if (window.AndroidBridge) {
                window.AndroidBridge.showToast(message);
            } else {
                console.log(`Toast: ${message}`);
            }
        }
    }
};

export default React;
