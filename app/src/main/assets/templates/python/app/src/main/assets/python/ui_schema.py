class Component:
    def __init__(self, type_name, **kwargs):
        self.type = type_name
        self.properties = kwargs

    def to_dict(self):
        # Recursively convert children if they exist
        props = self.properties.copy()
        if "children" in props:
            props["children"] = [child.to_dict() for child in props["children"]]

        return {
            "type": self.type,
            "properties": props
        }

class Scaffold(Component):
    def __init__(self, children):
        super().__init__("Scaffold", children=children)

class Column(Component):
    def __init__(self, children, vertical_arrangement="Top", horizontal_alignment="Start"):
        super().__init__("Column", children=children, verticalArrangement=vertical_arrangement, horizontalAlignment=horizontal_alignment)

class Row(Component):
    def __init__(self, children, horizontal_arrangement="Start", vertical_alignment="Top"):
        super().__init__("Row", children=children, horizontalArrangement=horizontal_arrangement, verticalAlignment=vertical_alignment)

class Text(Component):
    def __init__(self, text, font_size=14, color="#000000"):
        super().__init__("Text", text=text, fontSize=font_size, color=color)

class Button(Component):
    def __init__(self, text, on_click):
        super().__init__("Button", text=text, onClick=on_click)

class TextField(Component):
    def __init__(self, value, on_value_change):
        super().__init__("TextField", value=value, onValueChange=on_value_change)
