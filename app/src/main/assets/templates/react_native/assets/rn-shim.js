
const React = {
    createElement: (tag, props, ...children) => {
        if (typeof tag === 'function') return tag(props);

        let htmlTag = tag;
        let defaultStyle = {};

        // Map RN components to HTML tags and default styles
        if (tag === 'View') {
            htmlTag = 'div';
            defaultStyle = { display: 'flex', flexDirection: 'column', position: 'relative', boxSizing: 'border-box' };
        }
        else if (tag === 'Text') {
            htmlTag = 'span';
            defaultStyle = { display: 'inline-block' };
        }
        else if (tag === 'ScrollView') {
            htmlTag = 'div';
            defaultStyle = { overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column' };
        }
        else if (tag === 'Image') {
            htmlTag = 'img';
            defaultStyle = { maxWidth: '100%', height: 'auto' };
        }
        else if (tag === 'TextInput') {
            htmlTag = 'input';
            defaultStyle = { borderWidth: '1px', borderColor: 'gray', padding: '8px', fontSize: '16px' };
        }
        else if (tag === 'Button') {
            htmlTag = 'button';
            defaultStyle = { padding: '10px', backgroundColor: '#2196F3', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' };
        }
        else if (tag === 'TouchableOpacity') {
            htmlTag = 'div';
            defaultStyle = { cursor: 'pointer' };
        }

        const el = document.createElement(htmlTag);

        // Apply default styles
        Object.assign(el.style, defaultStyle);

        if (props) {
            if (props.style) {
                Object.assign(el.style, props.style);
                // Flexbox fix for root
                if (props.style.flex === 1 && !el.parentElement) {
                     el.style.height = '100vh';
                }
            }

            // Events
            if (props.onPress) el.onclick = props.onPress;

            // Image source
            if (tag === 'Image' && props.source) {
                 if (props.source.uri) el.src = props.source.uri;
                 else el.src = props.source;
            }

            // TextInput handlers
            if (tag === 'TextInput') {
                 if (props.onChangeText) el.oninput = (e) => props.onChangeText(e.target.value);
                 if (props.value !== undefined) el.value = props.value;
                 if (props.placeholder) el.placeholder = props.placeholder;
                 if (props.secureTextEntry) el.type = 'password';
            }

            // Button props
            if (tag === 'Button') {
                if (props.title) el.innerText = props.title;
                if (props.color) el.style.backgroundColor = props.color;
                if (props.disabled) el.disabled = true;
            }

            if (props.accessibilityLabel) {
                el.setAttribute('aria-label', props.accessibilityLabel);
            }
        }

        children.forEach(child => {
            if (typeof child === 'string' || typeof child === 'number') {
                el.appendChild(document.createTextNode(child));
            } else if (Array.isArray(child)) {
                child.forEach(c => c && el.appendChild(c));
            } else if (child) {
                el.appendChild(child);
            }
        });

        return el;
    }
};

export const View = 'View';
export const Text = 'Text';
export const ScrollView = 'ScrollView';
export const Image = 'Image';
export const TextInput = 'TextInput';
export const Button = 'Button';
export const TouchableOpacity = 'TouchableOpacity';

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
        window.addEventListener('load', () => {
            const Root = compProvider();
            const app = React.createElement(Root);
            document.body.appendChild(app);
            console.log('App mounted');
        });
    }
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
