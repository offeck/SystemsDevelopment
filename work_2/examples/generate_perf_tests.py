import json
import random
import os

# Matrix size
N = 64

def generate_matrix(rows, cols):
    return [[random.randint(1, 10) for _ in range(cols)] for _ in range(rows)]

def mat_add(a, b):
    rows = len(a)
    cols = len(a[0])
    return [[a[i][j] + b[i][j] for j in range(cols)] for i in range(rows)]

def mat_mul(a, b):
    rows_a = len(a)
    cols_a = len(a[0])
    cols_b = len(b[0])
    result = [[0 for _ in range(cols_b)] for _ in range(rows_a)]
    for i in range(rows_a):
        for j in range(cols_b):
            val = 0
            for k in range(cols_a):
                val += a[i][k] * b[k][j]
            result[i][j] = val
    return result

def generate_parallel_test_2ops():
    # (A * B) + (C * D)
    A = generate_matrix(N, N)
    B = generate_matrix(N, N)
    C = generate_matrix(N, N)
    D = generate_matrix(N, N)
    
    res_AB = mat_mul(A, B)
    res_CD = mat_mul(C, D)
    res_final = mat_add(res_AB, res_CD)
    
    input_json = {
        "operator": "+",
        "operands": [
            {
                "operator": "*",
                "operands": [A, B]
            },
            {
                "operator": "*",
                "operands": [C, D]
            }
        ]
    }
    
    return "test_perf_parallel_2ops", input_json, {"result": res_final}

def generate_parallel_test_4ops():
    # ((A*B) + (C*D)) + ((E*F) + (G*H))
    # This structure allows 4 multiplications to be resolvable initially.
    
    A = generate_matrix(N, N)
    B = generate_matrix(N, N)
    C = generate_matrix(N, N)
    D = generate_matrix(N, N)
    E = generate_matrix(N, N)
    F = generate_matrix(N, N)
    G = generate_matrix(N, N)
    H = generate_matrix(N, N)
    
    res_AB = mat_mul(A, B)
    res_CD = mat_mul(C, D)
    res_EF = mat_mul(E, F)
    res_GH = mat_mul(G, H)
    
    res_left = mat_add(res_AB, res_CD)
    res_right = mat_add(res_EF, res_GH)
    res_final = mat_add(res_left, res_right)
    
    input_json = {
        "operator": "+",
        "operands": [
            {
                "operator": "+",
                "operands": [
                    { "operator": "*", "operands": [A, B] },
                    { "operator": "*", "operands": [C, D] }
                ]
            },
            {
                "operator": "+",
                "operands": [
                    { "operator": "*", "operands": [E, F] },
                    { "operator": "*", "operands": [G, H] }
                ]
            }
        ]
    }
    
    return "test_perf_parallel_4ops", input_json, {"result": res_final}

def save_test(name, input_data, output_data):
    base = os.path.join("work_2/examples", name)
    with open(base + ".json", 'w') as f:
        json.dump(input_data, f, indent=None) # Compact to save space
    with open(base + "_out.json", 'w') as f:
        json.dump(output_data, f, indent=None)
    print(f"Generated {name}")

if __name__ == "__main__":
    name, inp, out = generate_parallel_test_2ops()
    save_test(name, inp, out)
    
    name, inp, out = generate_parallel_test_4ops()
    save_test(name, inp, out)
