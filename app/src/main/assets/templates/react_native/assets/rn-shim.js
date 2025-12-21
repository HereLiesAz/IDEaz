
const React = {
    createElement: (tag, props, ...children) => {
        if (typeof tag === 'function') return tag(props);

        // Map RN components to HTML tags
        let htmlTag = tag;
        if (tag === 'View') htmlTag = 'div';
        if (tag === 'Text') htmlTag = 'span';

        const el = document.createElement(htmlTag);

        if (props) {
            if (props.style) {
                // Simple style mapping
                Object.assign(el.style, props.style);
                // Flexbox fix for full screen
                if (props.style.flex === 1 && !el.parentElement) {
                     el.style.height = '100vh';
                     el.style.display = 'flex';
                }
            }
            if (props.accessibilityLabel) {
                el.setAttribute('aria-label', props.accessibilityLabel);
            }
        }

        children.forEach(child => {
            if (typeof child === 'string') {
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

export const StyleSheet = {
    create: (styles) => styles
};

export const AppRegistry = {
    registerComponent: (name, compProvider) => {
        console.log(`Registering component ${name}`);
        window.addEventListener('load', () => {
            const Root = compProvider();
            // Root is a component function, call it to render
            const app = React.createElement(Root);
            document.body.appendChild(app);
            console.log('App mounted');
        });
    }
};

export default React;
